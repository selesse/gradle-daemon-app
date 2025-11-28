package com.selesse.gradle.daemon.platform.macos

import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MacOSPlistHandlerTest {

    @Test
    fun `install creates plist file with correct structure`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = MacOSPlistHandler(processExecutor = mockExecutor)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val plistPath = tempDir.resolve("test.plist").toString()
        val config = DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home",
            configPath = plistPath,
            logPath = tempDir.resolve("daemon.log").toString(),
            jvmArgs = listOf("-Xmx512m", "--enable-native-access=ALL-UNNAMED"),
            appArgs = listOf("--verbose"),
            keepAlive = true,
        )

        handler.install(config)

        val plistFile = File(plistPath)
        assertTrue(plistFile.exists(), "Plist file should be created")

        val content = plistFile.readText()

        // Verify key elements
        assertTrue(content.contains("<key>Label</key>"), "Should contain Label key")
        assertTrue(content.contains("<string>com.example.test-daemon</string>"), "Should contain service ID")
        assertTrue(content.contains("<key>ProgramArguments</key>"), "Should contain ProgramArguments key")
        assertTrue(content.contains("<string>${config.javaHome}/bin/java</string>"), "Should contain Java path")
        assertTrue(content.contains("<string>-Xmx512m</string>"), "Should contain JVM args")
        assertTrue(content.contains("<string>--enable-native-access=ALL-UNNAMED</string>"), "Should contain JVM args")
        assertTrue(content.contains("<string>-jar</string>"), "Should contain -jar flag")
        assertTrue(content.contains("<string>${jarFile.absolutePath}</string>"), "Should contain JAR path")
        assertTrue(content.contains("<string>--verbose</string>"), "Should contain app args")
        assertTrue(content.contains("<key>KeepAlive</key>"), "Should contain KeepAlive key")
        assertTrue(content.contains("<true/>"), "Should have KeepAlive set to true")
        assertTrue(content.contains("<key>StandardOutPath</key>"), "Should contain StandardOutPath")
        assertTrue(content.contains("<key>StandardErrorPath</key>"), "Should contain StandardErrorPath")
        assertTrue(content.contains("<string>${config.logPath}</string>"), "Should contain log path")
    }

    @Test
    fun `install with keepAlive false`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = MacOSPlistHandler(processExecutor = mockExecutor)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val plistPath = tempDir.resolve("test.plist").toString()
        val config = createConfig(tempDir, configPath = plistPath, keepAlive = false)

        handler.install(config)

        val content = File(plistPath).readText()
        assertTrue(content.contains("<false/>"), "Should have KeepAlive set to false")
    }

    @Test
    fun `start daemon successfully returns PID`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("launchctl", "load"), stdout = "")
            .mockSuccess(
                listOf("launchctl", "list"),
                stdout = "12345\t0\tcom.example.test-daemon\n",
            )

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("launchctl", "load")))
        assertTrue(mockExecutor.wasExecuted(listOf("launchctl", "list")))
    }

    @Test
    fun `start daemon without PID in output returns null`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("launchctl", "load"), stdout = "")
            .mockSuccess(
                listOf("launchctl", "list"),
                stdout = "-\t0\tcom.example.test-daemon\n",
            )

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertNull(pid)
    }

    @Test
    fun `start daemon fails throws exception`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("launchctl", "load"),
                stderr = "Service already loaded",
                exitCode = 1,
            )

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val exception = assertThrows(RuntimeException::class.java) {
            handler.start(config)
        }

        assertTrue(exception.message?.contains("Failed to start daemon") == true)
        assertTrue(exception.message?.contains("Service already loaded") == true)
    }

    @Test
    fun `stop daemon successfully`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("launchctl", "unload"), stdout = "")

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val result = handler.stop(config)

        assertNull(result)
        assertTrue(mockExecutor.wasExecuted(listOf("launchctl", "unload")))
    }

    @Test
    fun `stop daemon when service not found succeeds`(@TempDir tempDir: Path) {
        // Exit code 3 means service not found, which we consider success
        val mockExecutor = MockProcessExecutor()
            .mockCommand(listOf("launchctl", "unload"), exitCode = 3, stderr = "Service not found")

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        assertDoesNotThrow {
            handler.stop(config)
        }
    }

    @Test
    fun `getStatus when daemon is running`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("launchctl", "list"),
                stdout = "12345\t0\tcom.example.test-daemon\n",
            )

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
        assertEquals("Daemon is running as LaunchAgent", status.details)
        assertEquals(config.configPath, status.configPath)
        assertEquals(config.logPath, status.logPath)
    }

    @Test
    fun `getStatus when daemon is not running`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("launchctl", "list"),
                stdout = "54321\t0\tcom.other.service\n",
            )

        val handler = MacOSPlistHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Daemon is not running", status.details)
        assertEquals(config.configPath, status.configPath)
        assertEquals(config.logPath, status.logPath)
    }

    @Test
    fun `cleanup removes plist file`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = MacOSPlistHandler(processExecutor = mockExecutor)

        val plistPath = tempDir.resolve("test.plist").toFile()
        plistPath.writeText("dummy plist content")
        assertTrue(plistPath.exists())

        val config = createConfig(tempDir, configPath = plistPath.absolutePath)

        handler.cleanup(config)

        assertFalse(plistPath.exists(), "Plist file should be deleted")
    }

    @Test
    fun `cleanup when plist file does not exist`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = MacOSPlistHandler(processExecutor = mockExecutor)

        val plistPath = tempDir.resolve("nonexistent.plist").toString()
        val config = createConfig(tempDir, configPath = plistPath)

        assertDoesNotThrow {
            handler.cleanup(config)
        }
    }

    @Test
    fun `getDefaultConfigPath returns correct path`() {
        val handler = MacOSPlistHandler()
        val serviceId = "com.example.test-daemon"

        val path = handler.getDefaultConfigPath(serviceId, null)

        val expectedPath = "${System.getProperty("user.home")}/Library/LaunchAgents/$serviceId.plist"
        assertEquals(expectedPath, path)
    }

    private fun createConfig(
        tempDir: Path,
        configPath: String = tempDir.resolve("test.plist").toString(),
        keepAlive: Boolean = true,
    ): DaemonConfig {
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        if (!jarFile.exists()) {
            jarFile.createNewFile()
        }

        return DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home",
            configPath = configPath,
            logPath = tempDir.resolve("daemon.log").toString(),
            jvmArgs = listOf("-Xmx512m"),
            appArgs = emptyList(),
            keepAlive = keepAlive,
        )
    }
}
