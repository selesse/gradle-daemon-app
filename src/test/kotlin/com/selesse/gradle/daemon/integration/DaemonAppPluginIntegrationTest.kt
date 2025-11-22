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

        assertTrue(result.output.contains("installDaemon"), "Should have installDaemon task")
        assertTrue(result.output.contains("startDaemon"), "Should have startDaemon task")
        assertTrue(result.output.contains("stopDaemon"), "Should have stopDaemon task")
        assertTrue(result.output.contains("restartDaemon"), "Should have restartDaemon task")
        assertTrue(result.output.contains("daemonStatus"), "Should have daemonStatus task")
        assertTrue(result.output.contains("uninstallDaemon"), "Should have uninstallDaemon task")
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
            .withArguments("installDaemon")
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
                    useStartupFolder = false
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
}
