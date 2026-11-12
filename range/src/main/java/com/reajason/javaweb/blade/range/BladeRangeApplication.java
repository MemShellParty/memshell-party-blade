package com.reajason.javaweb.blade.range;

import com.hellokaton.blade.Blade;
import com.hellokaton.blade.mvc.RouteContext;

import java.util.Base64;

public class BladeRangeApplication {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        Blade.create()
                .devMode(false)
                .get("/", ctx -> ctx.text("blade-range"))
                .get("/health", ctx -> ctx.text("ok"))
                .get("/hello", ctx -> ctx.text("hello-blade"))
                .post("/loader", BladeRangeApplication::load)
                .listen(port)
                .start();
    }

    private static void load(RouteContext ctx) {
        try {
            String body = ctx.bodyToString().trim();
            byte[] classBytes = Base64.getDecoder().decode(body);
            ClassLoader parent = Thread.currentThread().getContextClassLoader();
            Class<?> injectorClass = new ClassLoader(parent) {
                Class<?> define(byte[] bytes) {
                    return defineClass(null, bytes, 0, bytes.length);
                }
            }.define(classBytes);
            injectorClass.getDeclaredConstructor().newInstance();
            ctx.text("ok");
        } catch (Throwable e) {
            e.printStackTrace();
            ctx.status(500).text("inject failed: " + e);
        }
    }
}
