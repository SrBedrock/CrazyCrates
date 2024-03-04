import io.papermc.hangarpublishplugin.model.Platforms
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.ByteArrayOutputStream

plugins {
    id("io.papermc.hangar-publish-plugin")

    id("com.github.johnrengelman.shadow")

    id("com.modrinth.minotaur")

    `java-library`

    `maven-publish`
}

repositories {
    maven("https://repo.crazycrew.us/snapshots/")

    maven("https://repo.crazycrew.us/releases/")

    maven("https://jitpack.io/")

    mavenCentral()
}

// The commit id for the "main" branch prior to merging a pull request.
val start = "8f6f4dd"

// The commit id BEFORE merging the pull request so before "Merge pull request #30"
val end = "8f6f4dd"

val commitLog = getGitHistory().joinToString(separator = "") { formatGitLog(it) }

fun getGitHistory(): List<String> {
    val output: String = ByteArrayOutputStream().use { outputStream ->
        project.exec {
            executable("git")
            args("log",  "$start..$end", "--format=format:%h %s")
            standardOutput = outputStream
        }

        outputStream.toString()
    }

    return output.split("\n")
}

fun formatGitLog(commitLog: String): String {
    val hash = commitLog.take(7)
    val message = commitLog.substring(8) // Get message after commit hash + space between
    return "[$hash](https://github.com/Crazy-Crew/${rootProject.name}/commit/$hash) $message<br>"
}

val changes = """
${rootProject.file("CHANGELOG.md").readText(Charsets.UTF_8)} 
## Commits  
<details>  
<summary>Other</summary>

$commitLog
</details>
""".trimIndent()

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    shadowJar {
        archiveClassifier.set("")

        exclude("META-INF/**")
    }

}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of("17"))
}