pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots") {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

rootProject.name = "memshell-party-blade"

include("plugin")
include("range")
include("integration-test")
