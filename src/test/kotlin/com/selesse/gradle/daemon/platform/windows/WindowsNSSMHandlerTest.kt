package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WindowsNSSMHandlerTest {

    @Test
    fun `install creates NSSM service with correct parameters`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("net", "session"))
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        handler.install(config)

        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "install", "com_example_test-daemon")))
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppDirectory")))
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppParameters")))
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "Start")))
    }

    @Test
    fun `install with keepAlive true sets restart behavior`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, keepAlive = true)

        handler.install(config)

        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppExit")))
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppRestartDelay")))
    }

    @Test
    fun `install with keepAlive false sets exit behavior`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, keepAlive = false)

        handler.install(config)

        val commands = mockExecutor.getExecutedCommands()
        val appExitCommand = commands.find {
            it.size >= 5 && it[0] == "nssm.exe" && it[1] == "set" && it[3] == "AppExit" && it[4] == "Default"
        }
        assertNotNull(appExitCommand)
        assertEquals("Exit", appExitCommand?.getOrNull(5))
    }

    @Test
    fun `install with log path configures logging`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, logPath = "C:\\logs\\daemon.log")

        handler.install(config)

        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppStdout")))
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "set", "com_example_test-daemon", "AppStderr")))
    }

    @Test
    fun `install throws exception on failure`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(listOf("nssm.exe", "install"), stderr = "Access denied", exitCode = 1)

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val exception = assertThrows(RuntimeException::class.java) {
            handler.install(config)
        }
        assertTrue(exception.message!!.contains("Access denied"))
    }

    @Test
    fun `start service successfully`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "start"))
            .mockSuccess(listOf("sc", "queryex"), stdout = "PID                : 12345")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "start", "com_example_test-daemon")))
    }

    @Test
    fun `start service returns null on failure`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(listOf("nssm.exe", "start"), stderr = "Service not found", exitCode = 1)

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertNull(pid)
    }

    @Test
    fun `stop service successfully`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "get"))
            .mockSuccess(listOf("sc", "queryex"), stdout = "PID                : 12345")
            .mockSuccess(listOf("nssm.exe", "stop"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "stop", "com_example_test-daemon")))
    }

    @Test
    fun `stop service returns null on failure`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "get"))
            .mockSuccess(listOf("sc", "queryex"), stdout = "PID                : 12345")
            .mockFailure(listOf("nssm.exe", "stop"), stderr = "Service not running", exitCode = 1)

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.stop(config)

        assertNull(pid)
    }

    @Test
    fun `getStatus when service is running`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "status"), stdout = "SERVICE_RUNNING")
            .mockSuccess(listOf("nssm.exe", "get"))
            .mockSuccess(listOf("sc", "queryex"), stdout = "PID                : 12345")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
        assertEquals("Service is running", status.details)
    }

    @Test
    fun `getStatus when service is stopped`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "status"), stdout = "SERVICE_STOPPED")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Service is stopped", status.details)
    }

    @Test
    fun `getStatus when service is paused`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "status"), stdout = "SERVICE_PAUSED")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Service is stopped", status.details)
    }

    @Test
    fun `getStatus when service does not exist`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "status"), stdout = "Service com_example_test-daemon doesn't exist")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertNull(status.pid)
        assertEquals("Service is not installed", status.details)
    }

    @Test
    fun `cleanup removes service`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "remove"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        handler.cleanup(config)

        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "remove", "com_example_test-daemon", "confirm")))
    }

    @Test
    fun `cleanup handles non-existent service gracefully`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("nssm.exe", "remove"),
                stderr = "Service com_example_test-daemon doesn't exist",
                exitCode = 1,
            )

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        // Should not throw exception
        assertDoesNotThrow {
            handler.cleanup(config)
        }
    }

    @Test
    fun `getDefaultConfigPath returns empty string`() {
        val handler = WindowsNSSMHandler(nssmPathOverride = "nssm.exe", skipAdminCheck = true)
        val path = handler.getDefaultConfigPath("com.example.test-daemon", null)
        assertEquals("", path)
    }

    @Test
    fun `install fails with helpful message when not running as admin`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockFailure(listOf("net", "session"), stderr = "Access is denied.", exitCode = 1)

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
        )
        val config = createConfig(tempDir)

        val exception = assertThrows(RuntimeException::class.java) {
            handler.install(config)
        }
        assertTrue(exception.message!!.contains("Administrator privileges required"))
        assertTrue(exception.message!!.contains("Run as administrator"))
    }

    @Test
    fun `uses cached nssm exe if already downloaded`(@TempDir tempDir: Path) {
        // NSSM is now stored in version subdirectories: nssm/{version}/nssm.exe
        val nssmExePath = tempDir.resolve("nssm/2.24/nssm.exe")
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(nssmExePath.toString(), "status"), stdout = "SERVICE_STOPPED")

        // Pre-create the nssm.exe file to simulate already downloaded
        val nssmVersionDir = tempDir.resolve("nssm/2.24").toFile()
        nssmVersionDir.mkdirs()
        val nssmExe = nssmVersionDir.resolve("nssm.exe")
        nssmExe.writeText("fake nssm executable")

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmProvider = NSSMProvider(appDataDirOverride = tempDir.toFile()),
        )
        val config = createConfig(tempDir)

        // Should use the cached nssm.exe without downloading
        val status = handler.getStatus(config)

        assertFalse(status.running)
        assertTrue(
            mockExecutor.wasExecuted(
                listOf(nssmExePath.toString(), "status"),
            ),
        )
    }

    @Test
    fun `sanitizeServiceName replaces dots with underscores`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, serviceId = "com.example.my-app.daemon")

        handler.install(config)

        assertTrue(mockExecutor.wasExecuted(listOf("nssm.exe", "install", "com_example_my-app_daemon")))
    }

    @Test
    fun `install includes JVM args in parameters`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, jvmArgs = listOf("-Xmx512m", "-Xms256m"))

        handler.install(config)

        val commands = mockExecutor.getExecutedCommands()
        val appParamsCommand = commands.find {
            it.size >= 5 && it[0] == "nssm.exe" && it[1] == "set" && it[3] == "AppParameters"
        }
        assertNotNull(appParamsCommand)
        val params = appParamsCommand?.getOrNull(4) ?: ""
        assertTrue(params.contains("-Xmx512m"))
        assertTrue(params.contains("-Xms256m"))
        assertTrue(params.contains("-jar"))
    }

    @Test
    fun `install includes app args in parameters`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "install"))
            .mockSuccess(listOf("nssm.exe", "set"))

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir, appArgs = listOf("--config", "app.yml"))

        handler.install(config)

        val commands = mockExecutor.getExecutedCommands()
        val appParamsCommand = commands.find {
            it.size >= 5 && it[0] == "nssm.exe" && it[1] == "set" && it[3] == "AppParameters"
        }
        assertNotNull(appParamsCommand)
        val params = appParamsCommand?.getOrNull(4) ?: ""
        assertTrue(params.contains("--config"))
        assertTrue(params.contains("app.yml"))
    }

    @Test
    fun `getServicePid parses sc queryex output correctly`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "start"))
            .mockSuccess(
                listOf("sc", "queryex"),
                stdout = """
                SERVICE_NAME: com_example_test-daemon
                        TYPE               : 10  WIN32_OWN_PROCESS
                        STATE              : 4  RUNNING
                                                (STOPPABLE, PAUSABLE, ACCEPTS_SHUTDOWN)
                        WIN32_EXIT_CODE    : 0  (0x0)
                        SERVICE_EXIT_CODE  : 0  (0x0)
                        CHECKPOINT         : 0x0
                        WAIT_HINT          : 0x0
                        PID                : 98765
                        FLAGS              :
                """.trimIndent(),
            )

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertEquals(98765L, pid)
    }

    @Test
    fun `getServicePid returns null when sc queryex fails`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("nssm.exe", "start"))
            .mockFailure(listOf("sc", "queryex"), stderr = "Service not found", exitCode = 1)

        val handler = WindowsNSSMHandler(
            processExecutor = mockExecutor,
            nssmPathOverride = "nssm.exe",
            skipAdminCheck = true,
        )
        val config = createConfig(tempDir)

        val pid = handler.start(config)

        assertNull(pid)
    }

    private fun createConfig(
        tempDir: Path,
        serviceId: String = "com.example.test-daemon",
        jarName: String = "test-daemon.jar",
        jvmArgs: List<String> = listOf("-Xmx512m"),
        appArgs: List<String> = emptyList(),
        keepAlive: Boolean = true,
        logPath: String = "",
    ): DaemonConfig {
        val jarFile = tempDir.resolve(jarName).toFile()
        if (!jarFile.exists()) {
            jarFile.writeText("dummy jar content")
        }

        return DaemonConfig(
            serviceId = serviceId,
            jarFile = jarFile,
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = "",
            logPath = logPath,
            jvmArgs = jvmArgs,
            appArgs = appArgs,
            keepAlive = keepAlive,
        )
    }
}
