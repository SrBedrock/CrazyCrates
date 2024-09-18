plugins {
    `java-library`
    `maven-publish`
}

repositories {
    maven("https://repo.crazycrew.us/snapshots/")
    maven("https://repo.crazycrew.us/releases/")
    maven("https://jitpack.io/")
    mavenCentral()
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of("21"))
}