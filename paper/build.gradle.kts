plugins {
    id("paper-plugin")
    id("com.gradleup.shadow") version "8.3.1"
}

dependencies {
    api(project(":common"))

    implementation(libs.cluster.paper)

    implementation(libs.triumphcmds)

    implementation(libs.nbtapi)

    compileOnly(libs.decentholograms)

    compileOnly(libs.placeholderapi)

    compileOnly(libs.oraxen)

    compileOnly(fileTree("libs").include("*.jar"))
}

tasks {
    shadowJar {
        listOf(
            "com.ryderbelserion.cluster.paper",
            "de.tr7zw.changeme.nbtapi",
            "dev.triumphteam.cmd",
            "org.bstats"
        ).forEach {
            relocate(it, "libs.$it")
        }
    }

    processResources {
        val properties = hashMapOf(
            "name" to rootProject.name,
            "version" to project.version,
            "group" to rootProject.group,
            "description" to rootProject.description,
            "apiVersion" to providers.gradleProperty("apiVersion").get(),
            "authors" to providers.gradleProperty("authors").get(),
            "website" to providers.gradleProperty("website").get()
        )

        inputs.properties(properties)

        filesMatching("plugin.yml") {
            expand(properties)
        }
    }
}