pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.replaymod.preprocess" -> {
                    useModule("com.github.ReplayMod:preprocessor:${requested.version}")
                }
            }
        }
    }
}


rootProject.name = "altoclef"
rootProject.buildFileName = "root.gradle.kts"

val targetVersion = "1.20.1"

include(":$targetVersion")
project(":$targetVersion").apply {
    projectDir = file("versions/$targetVersion")
    buildFileName = "../../build.gradle"
    name = targetVersion
}
