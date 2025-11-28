package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.windows.WindowsHandler
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WindowsHandlerTest {

    @Test
    fun `useStartupFolder defaults to true`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        assertTrue(extension.windows.useStartupFolder, "useStartupFolder should default to true")
    }

    @Test
    fun `useStartupFolder can be disabled`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.windows.useStartupFolder = false

        assertFalse(extension.windows.useStartupFolder)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `handler install creates necessary directories`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.serviceId.set("com.example.test-daemon")
        extension.releaseDir.set(tempDir.resolve("release").toFile())

        val handler = WindowsHandler()
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.writeText("dummy jar content")

        val javaHome = "C:\\Program Files\\Java\\jdk-21"

        // This won't actually install to Windows startup folder in tests,
        // but we can verify the handler doesn't throw errors
        assertDoesNotThrow {
            handler.install(project, extension, jarFile, javaHome)
        }
    }
}
