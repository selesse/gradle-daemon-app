package com.selesse.gradle.daemon

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DaemonAppPluginTest {

    @Test
    fun `plugin registers daemonApp extension`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.findByType(DaemonAppExtension::class.java)
        assertNotNull(extension, "daemonApp extension should be registered")
    }

    @Test
    fun `plugin registers all daemon tasks`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val expectedTasks = listOf(
            "installDaemon",
            "startDaemon",
            "stopDaemon",
            "restartDaemon",
            "daemonStatus",
            "daemonLogs",
            "uninstallDaemon",
        )

        for (taskName in expectedTasks) {
            val task = project.tasks.findByName(taskName)
            assertNotNull(task, "Task '$taskName' should be registered")
            assertEquals("daemon", task?.group, "Task '$taskName' should be in 'daemon' group")
        }
    }

    @Test
    fun `extension default values are set correctly`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        assertTrue(extension.keepAlive.get(), "keepAlive should default to true")
        assertTrue(extension.jvmArgs.get().isEmpty(), "jvmArgs should default to empty list")
        assertTrue(extension.appArgs.get().isEmpty(), "appArgs should default to empty list")
    }

    @Test
    fun `extension can be configured`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        extension.serviceId.set("com.example.test-service")
        extension.jvmArgs.set(listOf("-Xmx512m", "-Xms256m"))
        extension.appArgs.set(listOf("--config", "/path/to/config"))
        extension.keepAlive.set(false)

        assertEquals("com.example.test-service", extension.serviceId.get())
        assertEquals(listOf("-Xmx512m", "-Xms256m"), extension.jvmArgs.get())
        assertEquals(listOf("--config", "/path/to/config"), extension.appArgs.get())
        assertFalse(extension.keepAlive.get())
    }

    @Test
    fun `macOS config can be configured`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        extension.macOS {
            plistPath = "/custom/path/to/service.plist"
        }

        assertEquals("/custom/path/to/service.plist", extension.macOS.plistPath)
    }

    @Test
    fun `windows config can be configured`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        extension.windows {
            useStartupFolder = false
        }

        assertFalse(extension.windows.useStartupFolder)
    }

    @Test
    fun `linux config can be configured`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        extension.linux {
            userService = false
            servicePath = "/custom/systemd/service.path"
        }

        assertFalse(extension.linux.userService)
        assertEquals("/custom/systemd/service.path", extension.linux.servicePath)
    }
}
