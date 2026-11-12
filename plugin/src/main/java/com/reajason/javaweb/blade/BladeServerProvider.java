package com.reajason.javaweb.blade;

import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.spi.ServerMeta;
import com.reajason.javaweb.memshell.spi.ServerProvider;

@ServerMeta(name = "Blade", order = 1000)
public class BladeServerProvider implements ServerProvider {

    @Override
    public AbstractServer createServer() {
        return new BladeServer();
    }
}
