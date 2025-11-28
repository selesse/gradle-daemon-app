package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.linux.LinuxHandler
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LinuxHandlerTest {

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `systemd service file is generated with correct structure`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.serviceId.set("com.example.test-daemon")
        extension.jvmArgs.set(listOf("-Xmx512m", "--enable-native-access=ALL-UNNAMED"))
        extension.appArgs.set(listOf("--verbose"))

        val servicePath = tempDir.resolve("test.service").toFile().absolutePath
        extension.linux.servicePath = servicePath

        val handler = LinuxHandler()
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val javaHome = "/usr/lib/jvm/java-21-openjdk"

        handler.install(project, extension, jarFile, javaHome)

        val serviceFile = File(servicePath)
        assertTrue(serviceFile.exists(), "Service file should be created")

        val content = serviceFile.readText()

        // Verify key elements
        assertTrue(content.contains("[Unit]"), "Should contain [Unit] section")
        assertTrue(content.contains("Description=com.example.test-daemon Daemon"), "Should contain description")
        assertTrue(content.contains("After=network.target"), "Should wait for network")

        assertTrue(content.contains("[Service]"), "Should contain [Service] section")
        assertTrue(content.contains("Type=simple"), "Should be simple service type")
        assertTrue(content.contains("ExecStart=$javaHome/bin/java"), "Should contain Java path")
        assertTrue(content.contains("-Xmx512m"), "Should contain JVM args")
        assertTrue(content.contains("--enable-native-access=ALL-UNNAMED"), "Should contain JVM args")
        assertTrue(content.contains("-jar ${jarFile.absolutePath}"), "Should contain JAR path")
        assertTrue(content.contains("--verbose"), "Should contain app args")
        assertTrue(content.contains("Restart=always"), "Should have restart policy")
        assertTrue(content.contains("RestartSec=10"), "Should have restart delay")
        assertTrue(content.contains("StandardOutput=append:"), "Should have stdout logging")
        assertTrue(content.contains("StandardError=append:"), "Should have stderr logging")

        assertTrue(content.contains("[Install]"), "Should contain [Install] section")
        assertTrue(content.contains("WantedBy=default.target"), "Should target default.target")
    }

    @Test
    fun `user service defaults to true`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        assertTrue(extension.linux.userService, "userService should default to true")
    }

    @Test
    fun `user service can be disabled`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.linux.userService = false

        assertFalse(extension.linux.userService)
    }
}
