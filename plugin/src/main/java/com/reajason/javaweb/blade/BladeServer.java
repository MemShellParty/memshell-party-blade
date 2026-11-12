package com.reajason.javaweb.blade;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.InjectorMapping;
import net.bytebuddy.jar.asm.Opcodes;

public class BladeServer extends AbstractServer {

    @Override
    public InjectorMapping getShellInjectorMapping() {
        return InjectorMapping.builder()
                .addInjector(ShellType.NETTY_HANDLER,
                        "com.reajason.javaweb.blade.injector.BladeNettyHandlerInjector")
                .build();
    }

    @Override
    public String getShellPackageName(String serverName) {
        return "com.hellokaton.blade.mvc.handler";
    }

    @Override
    public int getMinimumTargetJreVersion() {
        return Opcodes.V1_8;
    }
}
