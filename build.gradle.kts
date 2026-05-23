import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec

plugins {
    id("com.diffplug.spotless") version "8.5.1"
    id("net.kyori.indra") version "4.0.0"
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.4.1"
    id("com.xpdustry.toxopid") version "4.2.0"
}

val metadata =
    ModMetadata(
        name = "poly",
        displayName = "Poly",
        description = "Redirect players.",
        author = "xpdustry",
        version = "0.1.0",
        mainClass = "com.xpdustry.poly.PolyPlugin",
        repository = "xpdustry/poly",
        java = true,
        hidden = true,
        minGameVersion = "157",
        dependencies = mutableListOf(ModDependency("slf4md"), ModDependency("kotlin-runtime")),
    )

metadata.version += if (findProperty("is_release").toString().toBoolean()) "" else "-SNAPSHOT"
version = metadata.version
group = "com.xpdustry"
description = metadata.description

repositories {
    mavenCentral()
    anukeXpdustry()
}

spotless {
    kotlin {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeader("// SPDX-License-Identifier: MIT")
    }
    kotlinGradle {
        ktlint()
    }
}

toxopid {
    compileVersion = "v" + metadata.minGameVersion
    platforms = setOf(ModPlatform.SERVER)
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly("org.slf4j:slf4j-api:2.0.18")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("dev.kord:kord-core:0.18.1")
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    exclude("org.jetbrains.kotlin", "kotlin-reflect")

    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")

    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core")
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json")

    exclude("org.jetbrains.kotlinx", "kotlinx-datetime")
}

indra {
    javaVersions {
        target(25)
        minimumToolchain(25)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    if (metadata.repository.isNotBlank()) {
        val repo = metadata.repository.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            organization {
                name = "xpdustry"
                url = "https://www.xpdustry.com"
            }

            developers {
                developer {
                    id = "phinner"
                    timezone = "Europe/Brussels"
                }
            }
        }
    }
}

val generateMetadataFile by tasks.registering {
    inputs.property("metadata", metadata)
    val output = temporaryDir.resolve("plugin.json")
    outputs.file(output)
    doLast { output.writeText(ModMetadata.toJson(metadata)) }
}

tasks.shadowJar {
    archiveFileName = "${project.name}.jar"
    archiveClassifier = "plugin"
    from(generateMetadataFile)
    from(rootProject.file("LICENSE.md")) { into("META-INF") }

    fun ezRelocate(
        pkg: String,
        module: String = pkg.split(".").last(),
    ) = relocate(pkg, "com.xpdustry.poly.shadow.$module")

    ezRelocate("dev.kord")
    ezRelocate("io.ktor")
    ezRelocate("io.github.oshai.kotlinlogging")
    ezRelocate("okhttp3")
    ezRelocate("okio")

    minimize()
}

tasks.withType<MindustryExec> {
    jvmArguments.add("--enable-native-access=ALL-UNNAMED")
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md.jar"
    version = "v1.3.0"
}

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "kotlin-runtime"
    asset = "kotlin-runtime.jar"
    version = "v4.3.6+k.2.3.20"
}

tasks.named<MindustryExec>(MindustryExec.SERVER_EXEC_TASK_NAME) {
    mods.from(downloadSlf4md, downloadKotlinRuntime)
}

configurations.runtimeClasspath {
    exclude(group = "org.slf4j")
    exclude(group = "com.google.code.findbugs", module = "jsr305")
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}
