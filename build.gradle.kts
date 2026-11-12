plugins {
    id("com.gradleup.shadow") version "9.4.1" apply false
}

allprojects {
    group = "io.github.reajason"
    version = "3.2.0"

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
