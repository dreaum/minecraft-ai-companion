plugins {
    id("fabric-loom") version "1.7-SNAPSHOT" apply false
    id("com.replaymod.preprocess") version "221276c7d4316055744c499bb66dbc5b9d0a508c"
}

subprojects {
    repositories {
        //mavenLocal()
        mavenCentral()
        maven("https://libraries.minecraft.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }
}

preprocess {
    createNode("1.20.1", 12001, "yarn")
}
