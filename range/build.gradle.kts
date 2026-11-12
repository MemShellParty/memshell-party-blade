plugins {
    id("application")
    alias(libs.plugins.shadow)
}

description = "Blade target range for the memshell integration test"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    implementation(libs.blade.core)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("com.reajason.javaweb.blade.range.BladeRangeApplication")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("blade-range")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
}
