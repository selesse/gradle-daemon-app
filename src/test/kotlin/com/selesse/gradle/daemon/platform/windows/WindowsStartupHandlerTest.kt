package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WindowsStartupHandlerTest {

    @Test
    fun `install with useStartupFolder copies JAR to startup folder`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = WindowsStartupHandler(processExecutor = mockExecutor, useStartupFolder = true)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.writeText("dummy jar content")

        val config = createConfig(tempDir)

        // We can't actually test the copy to the real startup folder,
        // but we can verify the method doesn't throw
        assertDoesNotThrow {
            handler.install(config)
        }
    }

    @Test
    fun `install with useStartupFolder false does nothing`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = WindowsStartupHandler(processExecutor = mockExecutor, useStartupFolder = false)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.writeText("dummy jar content")

        val config = createConfig(tempDir)

        assertDoesNotThrow {
            handler.install(config)
        }
    }

    @Test
    fun `stop daemon finds and kills process`(@TempDir tempDir: Path) {
        val jarName = "test-daemon.jar"
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\$jarName                      12345
                """.trimIndent(),
            )
            .mockSuccess(listOf("taskkill"), stdout = "SUCCESS: The process with PID 12345 has been terminated.\n")

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir, jarName = jarName)

        val pid = handler.stop(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("wmic", "process")))
        assertTrue(mockExecutor.wasExecuted(listOf("taskkill")))
    }

    @Test
    fun `stop daemon when process not found returns null`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\other.jar                     54321
                """.trimIndent(),
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertNull(pid)
    }

    @Test
    fun `stop daemon when wmic fails returns null`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("wmic", "process"),
                stderr = "WMIC is deprecated",
                exitCode = 1,
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertNull(pid)
    }

    @Test
    fun `stop daemon when taskkill fails returns null`(@TempDir tempDir: Path) {
        val jarName = "test-daemon.jar"
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\$jarName                      12345
                """.trimIndent(),
            )
            .mockFailure(
                listOf("taskkill"),
                stderr = "ERROR: The process could not be terminated",
                exitCode = 1,
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir, jarName = jarName)

        val pid = handler.stop(config)

        assertNull(pid)
    }

    @Test
    fun `getStatus when daemon is running`(@TempDir tempDir: Path) {
        val jarName = "test-daemon.jar"
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\$jarName                      12345
                """.trimIndent(),
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir, jarName = jarName)

        val status = handler.getStatus(config)

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
        assertEquals("Daemon is running", status.details)
        assertNull(status.configPath)
        assertNull(status.logPath)
    }

    @Test
    fun `getStatus when daemon is not running`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\other.jar                     54321
                """.trimIndent(),
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Daemon is not running", status.details)
    }

    @Test
    fun `getStatus when wmic fails`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("wmic", "process"),
                stderr = "WMIC is deprecated",
                exitCode = 1,
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
    }

    @Test
    fun `cleanup with useStartupFolder removes JAR from startup folder`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = WindowsStartupHandler(processExecutor = mockExecutor, useStartupFolder = true)

        val config = createConfig(tempDir)

        // We can't actually test the removal from the real startup folder,
        // but we can verify the method doesn't throw
        assertDoesNotThrow {
            handler.cleanup(config)
        }
    }

    @Test
    fun `cleanup with useStartupFolder false does nothing`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
        val handler = WindowsStartupHandler(processExecutor = mockExecutor, useStartupFolder = false)

        val config = createConfig(tempDir)

        assertDoesNotThrow {
            handler.cleanup(config)
        }
    }

    @Test
    fun `getDefaultConfigPath returns empty string`() {
        val handler = WindowsStartupHandler()
        val path = handler.getDefaultConfigPath("com.example.test-daemon", null)
        assertEquals("", path)
    }

    @Test
    fun `findDaemonPid parses wmic output correctly with multiple processes`(@TempDir tempDir: Path) {
        val jarName = "test-daemon.jar"
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\other.jar                     11111
                    javaw.exe -jar C:\path\to\$jarName                      12345
                    javaw.exe -jar C:\path\to\another.jar                   22222
                """.trimIndent(),
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir, jarName = jarName)

        val status = handler.getStatus(config)

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
    }

    @Test
    fun `findDaemonPid handles malformed wmic output`(@TempDir tempDir: Path) {
        val jarName = "test-daemon.jar"
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(
                listOf("wmic", "process"),
                stdout = """
                    CommandLine                                              ProcessId
                    javaw.exe -jar C:\path\to\$jarName                      not-a-number
                """.trimIndent(),
            )

        val handler = WindowsStartupHandler(processExecutor = mockExecutor)
        val config = createConfig(tempDir, jarName = jarName)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
    }

    private fun createConfig(
        tempDir: Path,
        jarName: String = "test-daemon.jar",
    ): DaemonConfig {
        val jarFile = tempDir.resolve(jarName).toFile()
        if (!jarFile.exists()) {
            jarFile.writeText("dummy jar content")
        }

        return DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = "",
            logPath = "",
            jvmArgs = listOf("-Xmx512m"),
            appArgs = emptyList(),
            keepAlive = true,
        )
    }
}
