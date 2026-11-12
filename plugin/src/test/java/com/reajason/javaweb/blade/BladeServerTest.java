package com.reajason.javaweb.blade;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.spi.ServerMeta;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BladeServerTest {

    private final BladeServer server = new BladeServer();

    @Test
    void mapsNettyHandlerInjector() {
        assertEquals(
                "com.reajason.javaweb.blade.injector.BladeNettyHandlerInjector",
                server.getShellInjectorMapping().getInjector(ShellType.NETTY_HANDLER));
    }

    @Test
    void usesBladePackageForGeneratedShells() {
        assertEquals("com.hellokaton.blade.mvc.handler", server.getShellPackageName("Blade"));
        assertTrue(server.getShellClassName("Blade", ShellType.NETTY_HANDLER)
                .startsWith("com.hellokaton.blade.mvc.handler."));
    }

    @Test
    void requiresJava8Bytecode() {
        assertEquals(Opcodes.V1_8, server.getMinimumTargetJreVersion());
    }

    @Test
    void providerRegistersBlade() {
        ServerMeta meta = BladeServerProvider.class.getAnnotation(ServerMeta.class);
        assertNotNull(meta);
        assertEquals("Blade", meta.name());
        assertTrue(new BladeServerProvider().createServer() instanceof BladeServer);
    }
}
