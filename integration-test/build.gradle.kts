plugins {
    id("java")
}

description = "End-to-end tests: plugin discovery, injection, and shell connectivity against the Blade range"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val externalPluginsDir = providers.gradleProperty("plugins.dir")
val pluginsDir = externalPluginsDir
    .map { file(it) }
    .orElse(layout.buildDirectory.dir("plugins").map { it.asFile })

dependencies {
    testImplementation("io.github.reajason:memshell-party-api:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-core:3.2.0-SNAPSHOT")
    testImplementation("io.github.reajason:memshell-party-shelltool-godzilla:3.0.0")
    testImplementation("io.github.reajason:memshell-party-shelltool-command:3.1.0-SNAPSHOT")
    // The plugin's injector template extends ChannelInitializer; the host classloader must be
    // able to resolve it during generation.
    testImplementation(libs.netty.all)
    // The Godzilla GUI artifact is not a headless client. The test carries a minimal
    // protocol client and payload so the build does not need the host source tree.
    testImplementation(libs.okhttp3)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val preparePlugins = tasks.register<Sync>("preparePlugins") {
    from(project(":plugin").tasks.named("jar"))
    into(pluginsDir)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":range:shadowJar")
    if (!externalPluginsDir.isPresent) {
        dependsOn(preparePlugins)
    }
    // Both are read at runtime by path, so Gradle cannot infer them as inputs on its own.
    inputs.dir(pluginsDir)
    inputs.file(project(":range").layout.buildDirectory.file("libs/blade-range.jar"))
    systemProperty("memshell.plugins.dir", pluginsDir.get().absolutePath)
    systemProperty(
        "blade.range.jar",
        project(":range").layout.buildDirectory.file("libs/blade-range.jar").get().asFile.absolutePath,
    )
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
