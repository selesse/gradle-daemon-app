package com.selesse.gradle.daemon.integration

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DaemonAppPluginIntegrationTest {

    @Test
    fun `plugin can be applied successfully`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            daemonApp {
                serviceId.set("com.example.test-daemon")
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
                archiveVersion.set("1.0.0")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("tasks", "--all")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("daemonInstall"), "Should have daemonInstall task")
        assertTrue(result.output.contains("daemonStart"), "Should have daemonStart task")
        assertTrue(result.output.contains("daemonStop"), "Should have daemonStop task")
        assertTrue(result.output.contains("daemonRestart"), "Should have daemonRestart task")
        assertTrue(result.output.contains("daemonStatus"), "Should have daemonStatus task")
        assertTrue(result.output.contains("daemonUninstall"), "Should have daemonUninstall task")
    }

    @Test
    fun `plugin fails when serviceId is not configured`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("daemonInstall")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(
            result.output.contains("daemonApp.serviceId must be configured"),
            "Should fail with serviceId error",
        )
    }

    @Test
    fun `plugin auto-detects shadowJar task`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            daemonApp {
                serviceId.set("com.example.test-daemon")
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
                archiveVersion.set("1.0.0")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        // Create a dummy Main.java file
        val srcDir = tempDir.resolve("src/main/java/com/example").toFile()
        srcDir.mkdirs()
        val mainFile = File(srcDir, "Main.java")
        mainFile.writeText(
            """
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("shadowJar", "--info")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shadowJar")?.outcome)
    }

    @Test
    fun `plugin can configure custom JVM args`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            daemonApp {
                serviceId.set("com.example.test-daemon")
                jvmArgs.set(listOf("-Xmx512m", "-Xms256m"))
                appArgs.set(listOf("--config", "/path/to/config"))
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("tasks")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `plugin auto-detects java toolchain configuration`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }

            daemonApp {
                serviceId.set("com.example.test-daemon")
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
                archiveVersion.set("1.0.0")
            }

            tasks.register("printJavaLauncher") {
                doLast {
                    val extension = project.extensions.getByType(com.selesse.gradle.daemon.DaemonAppExtension::class.java)
                    if (extension.javaLauncher.isPresent) {
                        val launcher = extension.javaLauncher.get()
                        val javaHome = launcher.metadata.installationPath.asFile.absolutePath
                        println("JAVA_LAUNCHER_DETECTED: ${'$'}javaHome")
                    } else {
                        println("JAVA_LAUNCHER_NOT_DETECTED")
                    }
                }
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("printJavaLauncher")
            .withPluginClasspath()
            .build()

        assertTrue(
            result.output.contains("JAVA_LAUNCHER_DETECTED:"),
            "Should auto-detect java toolchain. Output was: ${result.output}",
        )
        assertFalse(
            result.output.contains("JAVA_LAUNCHER_NOT_DETECTED"),
            "javaLauncher should be present when toolchain is configured",
        )
    }

    @Test
    fun `platform-specific configuration works`(@TempDir tempDir: Path) {
        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            daemonApp {
                serviceId.set("com.example.test-daemon")

                macOS {
                    plistPath = "/custom/path/service.plist"
                }

                linux {
                    userService = false
                    servicePath = "/etc/systemd/system/test-daemon.service"
                }

                windows {
                    startupFolder()
                }
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments("tasks")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks")?.outcome)
    }

    @Test
    fun `read-only daemon tasks work with the configuration cache`(@TempDir tempDir: Path) {
        val plistPath = tempDir.resolve("service.plist").toString().replace("\\", "\\\\")
        val releaseDirPath = tempDir.resolve("release").toString().replace("\\", "\\\\")

        val buildFile = tempDir.resolve("build.gradle.kts").toFile()
        buildFile.writeText(
            """
            plugins {
                id("com.selesse.daemon-app")
                id("java")
            }

            daemonApp {
                serviceId.set("com.example.config-cache-test-daemon")
                releaseDir.set(file("$releaseDirPath"))

                macOS {
                    plistPath = "$plistPath"
                }
            }

            tasks.register<Jar>("shadowJar") {
                archiveBaseName.set("test-daemon")
                archiveVersion.set("1.0.0")
            }
            """.trimIndent(),
        )

        val settingsFile = tempDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText(
            """
            rootProject.name = "test-daemon"
            """.trimIndent(),
        )

        val srcDir = tempDir.resolve("src/main/java/com/example").toFile()
        srcDir.mkdirs()
        File(srcDir, "Main.java").writeText(
            """
            package com.example;

            public class Main {
                public static void main(String[] args) {
                }
            }
            """.trimIndent(),
        )

        val arguments = arrayOf("daemonStatus", "daemonLogs", "daemonUninstall", "--configuration-cache")

        val firstRun = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments(*arguments)
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":daemonStatus")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":daemonLogs")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":daemonUninstall")?.outcome)

        val secondRun = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments(*arguments)
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, secondRun.task(":daemonStatus")?.outcome)
        assertTrue(
            secondRun.output.contains("Reusing configuration cache"),
            "Second run should reuse the configuration cache. Output was: ${secondRun.output}",
        )
    }
}
