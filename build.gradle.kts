import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") version "8.5.1"
    id("net.kyori.indra") version "4.0.0"
    id("com.gradleup.shadow") version "9.4.1"
    id("com.xpdustry.toxopid") version "4.2.0"
    id("net.ltgt.errorprone") version "5.1.0"
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
        dependencies = mutableListOf(ModDependency("slf4md")),
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
    java {
        palantirJavaFormat()
        formatAnnotations()
        importOrder("", "\\#")
        forbidModuleImports()
        forbidWildcardImports()
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
    compileOnly("org.slf4j:slf4j-api:2.0.18")
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnlyApi("org.jspecify:jspecify:1.0.0")
    annotationProcessor("com.uber.nullaway:nullaway:0.13.4")
    testAnnotationProcessor("com.uber.nullaway:nullaway:0.13.4")
    errorprone("com.google.errorprone:error_prone_core:2.49.0")
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
}

tasks.withType<JavaCompile> {
    options.errorprone {
        disable("MissingSummary", "InlineMeSuggester")
        option("NullAway:OnlyNullMarked")
        check("NullAway", CheckSeverity.ERROR)
    }
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

tasks.named<MindustryExec>(MindustryExec.SERVER_EXEC_TASK_NAME) {
    mods.from(downloadSlf4md)
}
