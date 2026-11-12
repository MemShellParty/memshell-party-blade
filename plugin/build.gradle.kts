plugins {
    `java-library`
}

description = "Blade server provider plugin for memshell-party"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    // Host-provided at runtime. Bundling these into the plugin would duplicate class identities
    // and put a second copy of the SPI providers on the host classpath.
    compileOnly("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    compileOnly("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")
    // Pins the two shellTool contracts this plugin advertises. The full host distribution
    // already contains both providers; the plugin only adds the Blade server mapping.
    compileOnly("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    compileOnly("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    // The injector template extends ChannelInitializer, so generation-time template resolution
    // needs netty-transport on the plugin classloader. The host full distribution ships it.
    compileOnly(libs.netty.transport)

    testImplementation("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

base {
    archivesName.set("memshell-party-blade-plugin")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "memshell-party-blade-plugin",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "memshell-party",
        )
    }
}
