package com.hellokaton.blade.mvc;

import io.netty.channel.ChannelHandlerContext;

/**
 * Generation-time stub for Blade's WebContext. The generated injector runs in the target
 * application, where parent-first class loading resolves the real com.hellokaton.blade.mvc.WebContext
 * from the application class loader. This stub only lets the injector template link against Blade
 * without adding Blade as a build dependency. It is never executed.
 */
public class WebContext {

    public static WebContext get() {
        return null;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return null;
    }
}
