package com.selesse.gradle.daemon.platform.linux

import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LinuxSystemDHandlerTest {

    @Test
    fun `install creates systemd service file with correct structure`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "daemon-reload"), stdout = "")
            .mockSuccess(listOf("systemctl", "--user", "enable"), stdout = "")

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val servicePath = tempDir.resolve("test.service").toString()
        val config = DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "/usr/lib/jvm/java-21-openjdk",
            configPath = servicePath,
            logPath = tempDir.resolve("daemon.log").toString(),
            jvmArgs = listOf("-Xmx512m", "--enable-native-access=ALL-UNNAMED"),
            appArgs = listOf("--verbose"),
            keepAlive = true,
        )

        handler.install(config)

        val serviceFile = File(servicePath)
        assertTrue(serviceFile.exists(), "Service file should be created")

        val content = serviceFile.readText()

        // Verify [Unit] section
        assertTrue(content.contains("[Unit]"), "Should contain [Unit] section")
        assertTrue(content.contains("Description=com.example.test-daemon Daemon"), "Should contain description")
        assertTrue(content.contains("After=network.target"), "Should wait for network")

        // Verify [Service] section
        assertTrue(content.contains("[Service]"), "Should contain [Service] section")
        assertTrue(content.contains("Type=simple"), "Should be simple service type")
        assertTrue(content.contains("ExecStart=${config.javaHome}/bin/java"), "Should contain Java path")
        assertTrue(content.contains("-Xmx512m"), "Should contain JVM args")
        assertTrue(content.contains("--enable-native-access=ALL-UNNAMED"), "Should contain JVM args")
        assertTrue(content.contains("-jar ${jarFile.absolutePath}"), "Should contain JAR path")
        assertTrue(content.contains("--verbose"), "Should contain app args")
        assertTrue(content.contains("Restart=always"), "Should have restart policy")
        assertTrue(content.contains("RestartSec=10"), "Should have restart delay")
        assertTrue(content.contains("StandardOutput=append:${config.logPath}"), "Should have stdout logging")
        assertTrue(content.contains("StandardError=append:${config.logPath}"), "Should have stderr logging")

        // Verify [Install] section
        assertTrue(content.contains("[Install]"), "Should contain [Install] section")
        assertTrue(content.contains("WantedBy=default.target"), "Should target default.target")

        // Verify systemctl commands were executed
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "daemon-reload")))
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "enable")))
    }

    @Test
    fun `install with keepAlive false uses Restart=no`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "daemon-reload"), stdout = "")
            .mockSuccess(listOf("systemctl", "--user", "enable"), stdout = "")

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val servicePath = tempDir.resolve("test.service").toString()
        val config = createConfig(tempDir, configPath = servicePath, keepAlive = false)

        handler.install(config)

        val content = File(servicePath).readText()
        assertTrue(content.contains("Restart=no"), "Should have Restart=no")
    }

    @Test
    fun `start daemon successfully returns PID`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "start"), stdout = "")
            .mockSuccess(
                listOf("systemctl", "--user", "show"),
                stdout = "MainPID=12345\n",
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "start")))
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "show")))
    }

    @Test
    fun `start daemon when getPid fails returns null`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "start"), stdout = "")
            .mockFailure(
                listOf("systemctl", "--user", "show"),
                stderr = "Failed to get properties",
                exitCode = 1,
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertNull(pid)
    }

    @Test
    fun `start daemon fails throws exception`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("systemctl", "--user", "start"),
                stderr = "Failed to start service",
                exitCode = 1,
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val exception = assertThrows(RuntimeException::class.java) {
            handler.start(config)
        }

        assertTrue(exception.message?.contains("Failed to start daemon") == true)
        assertTrue(exception.message?.contains("Failed to start service") == true)
    }

    @Test
    fun `stop daemon successfully returns PID`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("systemctl", "--user", "show"),
                stdout = "MainPID=12345\n",
            )
            .mockSuccess(listOf("systemctl", "--user", "stop"), stdout = "")

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "stop")))
    }

    @Test
    fun `stop daemon when not running returns null`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("systemctl", "--user", "show"),
                stdout = "MainPID=0\n",
            )
            .mockFailure(
                listOf("systemctl", "--user", "stop"),
                stderr = "Service not found",
                exitCode = 5,
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertNull(pid)
    }

    @Test
    fun `getStatus when daemon is running`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("systemctl", "--user", "is-active"),
                stdout = "active\n",
            )
            .mockSuccess(
                listOf("systemctl", "--user", "show"),
                stdout = "MainPID=12345\n",
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
        assertEquals("Daemon is running as systemd service", status.details)
        assertEquals(config.configPath, status.configPath)
        assertEquals(config.logPath, status.logPath)
    }

    @Test
    fun `getStatus when daemon is inactive`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("systemctl", "--user", "is-active"),
                stdout = "inactive\n",
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Daemon is not running (status: inactive)", status.details)
        assertEquals(config.configPath, status.configPath)
        assertEquals(config.logPath, status.logPath)
    }

    @Test
    fun `getStatus when daemon is failed`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("systemctl", "--user", "is-active"),
                stdout = "failed\n",
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertEquals("Daemon is not running (status: failed)", status.details)
    }

    @Test
    fun `cleanup disables service and removes file`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "disable"), stdout = "")
            .mockSuccess(listOf("systemctl", "--user", "daemon-reload"), stdout = "")

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)

        val serviceFile = tempDir.resolve("test.service").toFile()
        serviceFile.writeText("dummy service content")
        assertTrue(serviceFile.exists())

        val config = createConfig(tempDir, configPath = serviceFile.absolutePath)

        handler.cleanup(config)

        assertFalse(serviceFile.exists(), "Service file should be deleted")
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "disable")))
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "daemon-reload")))
    }

    @Test
    fun `cleanup when service file does not exist`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "disable"), stdout = "")
            .mockSuccess(listOf("systemctl", "--user", "daemon-reload"), stdout = "")

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)

        val servicePath = tempDir.resolve("nonexistent.service").toString()
        val config = createConfig(tempDir, configPath = servicePath)

        assertDoesNotThrow {
            handler.cleanup(config)
        }

        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "disable")))
        assertTrue(mockExecutor.wasExecuted(listOf("systemctl", "--user", "daemon-reload")))
    }

    @Test
    fun `getDefaultConfigPath returns correct path`() {
        val handler = LinuxSystemDHandler()
        val serviceId = "com.example.test-daemon"

        val path = handler.getDefaultConfigPath(serviceId, null)

        val expectedPath = "${System.getProperty("user.home")}/.config/systemd/user/$serviceId.service"
        assertEquals(expectedPath, path)
    }

    @Test
    fun `service name includes dot service suffix`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("systemctl", "--user", "start"), stdout = "")
            .mockSuccess(
                listOf("systemctl", "--user", "show"),
                stdout = "MainPID=12345\n",
            )

        val handler = LinuxSystemDHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        handler.start(config)

        // Verify the service name includes .service suffix
        val commands = mockExecutor.getExecutedCommands()
        val startCommand = commands.first { it.contains("start") }
        assertTrue(startCommand.last().endsWith(".service"), "Service name should end with .service")
    }

    private fun createConfig(
        tempDir: Path,
        configPath: String = tempDir.resolve("test.service").toString(),
        keepAlive: Boolean = true,
    ): DaemonConfig {
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        if (!jarFile.exists()) {
            jarFile.createNewFile()
        }

        return DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "/usr/lib/jvm/java-21-openjdk",
            configPath = configPath,
            logPath = tempDir.resolve("daemon.log").toString(),
            jvmArgs = listOf("-Xmx512m"),
            appArgs = emptyList(),
            keepAlive = keepAlive,
        )
    }
}
