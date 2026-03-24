import java.security.MessageDigest

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("org.jetbrains.changelog") version "2.2.1"
    id("pl.allegro.tech.build.axion-release") version "1.18.2"
}

group = "com.selesse.gradle"
version = scmVersion.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

    testImplementation("org.assertj:assertj-core:3.24.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website.set("https://github.com/selesse/gradle-daemon-app")
    vcsUrl.set("https://github.com/selesse/gradle-daemon-app")

    plugins {
        create("daemonApp") {
            id = "com.selesse.daemon-app"
            implementationClass = "com.selesse.gradle.daemon.DaemonAppPlugin"
            displayName = "Daemon App Plugin"
            description = "Gradle plugin for installing and managing Java applications as background daemons"
            tags.set(listOf("daemon", "background", "service", "launchd", "systemd"))
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.1")
        }

        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter("5.10.1")

            dependencies {
                implementation(project())
                implementation("org.assertj:assertj-core:3.24.2")
                implementation(gradleTestKit())
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

gradlePlugin.testSourceSets(sourceSets["integrationTest"])

// WinSW binary bundled at src/main/resources/windows/WinSW.exe
// Downloaded from: https://github.com/winsw/winsw/releases/download/v3.0.0-alpha.11/WinSW-net461.exe
// WinSW does not publish checksums with its releases. The SHA-256 below was computed locally
// at the time the binary was added and serves as a pin against accidental corruption or
// substitution within this repo — not as verification of the upstream source.
val winswVersion = "v3.0.0-alpha.11"
val winswAsset = "WinSW-net461.exe"
val winswExpectedSha256 = "91bce26b4fa3a7534e7967c1804d7417737b7169014435e5b3b31924bf19f3ee"

tasks.register("verifyWinswChecksum") {
    group = "verification"
    description = "Verifies the bundled WinSW.exe SHA-256 matches the checksum recorded when the binary was added"

    val winswExe = file("src/main/resources/windows/WinSW.exe")

    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        winswExe.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }

        check(actualSha256 == winswExpectedSha256) {
            "WinSW checksum mismatch!\n" +
                "  Expected : $winswExpectedSha256\n" +
                "  Actual   : $actualSha256\n" +
                "  File     : ${winswExe.absolutePath}"
        }

        logger.lifecycle("WinSW checksum verified")
        logger.lifecycle("  Version  : $winswVersion ($winswAsset)")
        logger.lifecycle("  SHA-256  : $actualSha256")
        logger.lifecycle("  Release  : https://github.com/winsw/winsw/releases/tag/$winswVersion")
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
    dependsOn("verifyWinswChecksum")
}

spotless {
    kotlin {
        ktlint("1.0.1").editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
            ),
        )
        target("src/**/*.kt")
    }
    kotlinGradle {
        ktlint("1.0.1")
    }
}

if (System.getenv("CI") == null) {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        dependsOn("spotlessApply")
    }
}

scmVersion {
    tag {
        prefix.set("v")
    }
    nextVersion {
        suffix.set("SNAPSHOT")
        separator.set("-")
    }
}

changelog {
    version.set(project.version.toString())
    path.set(file("CHANGELOG.md").canonicalPath)
    header.set(provider { "${project.version}" })
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    combinePreReleases.set(false)
    repositoryUrl.set("https://github.com/selesse/gradle-daemon-app")
}
