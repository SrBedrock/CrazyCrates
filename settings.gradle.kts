pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")

        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")

        gradlePluginPortal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            version("cluster", "6.4")

            library("decentholograms", "com.github.decentsoftware-eu", "decentholograms").version("2.8.11")

            library("triumphcmds", "dev.triumphteam", "triumph-cmd-bukkit").version("2.0.0-SNAPSHOT")

            library("cluster_paper", "com.ryderbelserion.cluster", "paper").versionRef("cluster")
            library("cluster_api", "com.ryderbelserion.cluster", "paper").versionRef("cluster")

            library("placeholderapi", "me.clip", "placeholderapi").version("2.11.6")

            library("triumphgui", "dev.triumphteam", "triumph-gui").version("3.1.10")

            library("nbtapi", "de.tr7zw", "item-nbt-api").version("2.13.2")

            library("oraxen", "io.th0rgal", "oraxen").version("1.181.0")

            library("configme", "ch.jalu", "configme").version("1.4.1")
        }
    }
}

rootProject.name = "CrazyCrates"

include("api")
include("paper")
//include("fabric")
include("common")