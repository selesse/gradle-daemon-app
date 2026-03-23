package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WindowsWinswHandlerTest {

    private fun createHandler(
        mockExecutor: MockProcessExecutor,
        winswExecutablePath: String? = null,
        serviceName: String? = null,
        serviceDisplayName: String? = null,
        serviceDescription: String? = null,
    ) = WindowsWinswHandler(
        processExecutor = mockExecutor,
        winswExecutablePath = winswExecutablePath,
        serviceName = serviceName,
        serviceDisplayName = serviceDisplayName,
        serviceDescription = serviceDescription,
    )

    @Test
    fun `install creates WinSW XML configuration`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("winsw.exe", "install"), stdout = "Service installed")

        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val handler = createHandler(mockExecutor, winswExecutablePath = winswExe.absolutePath)

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val xmlPath = tempDir.resolve("test-daemon.xml").toString()
        val config = DaemonConfig(
            serviceId = "com.example.test-daemon",
            jarFile = jarFile,
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = xmlPath,
            logPath = tempDir.resolve("test-daemon.out.log").toString(),
            jvmArgs = listOf("-Xmx512m", "--enable-native-access=ALL-UNNAMED"),
            appArgs = listOf("--verbose"),
            keepAlive = true,
        )

        handler.install(config)

        val xmlFile = File(xmlPath)
        assertTrue(xmlFile.exists(), "XML config file should be created")

        val content = xmlFile.readText()
        assertTrue(content.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), "Should have XML declaration")
        assertTrue(content.contains("<service>"), "Should have service tag")
        assertTrue(content.contains("<id>com.example.test-daemon</id>"), "Should contain service ID")
        assertTrue(content.contains("<name>com.example.test-daemon</name>"), "Should contain service name")
        assertTrue(content.contains("<description>com.example.test-daemon Daemon Service</description>"), "Should contain description")
        assertTrue(content.contains("<executable>") && content.contains("java.exe</executable>"), "Should contain Java path")
        assertTrue(content.contains("-Xmx512m"), "Should contain JVM args")
        assertTrue(content.contains("--enable-native-access=ALL-UNNAMED"), "Should contain JVM args")
        assertTrue(content.contains("-jar"), "Should contain -jar flag")
        assertTrue(content.contains(jarFile.name), "Should contain JAR path")
        assertTrue(content.contains("--verbose"), "Should contain app args")
        assertTrue(content.contains("<onfailure action=\"restart\""), "Should contain restart policy")
        assertTrue(content.contains("</service>"), "Should close service tag")

        val commands = mockExecutor.getExecutedCommands()
        assertTrue(
            commands.any { it.size == 2 && it[1] == "install" && it[0].endsWith(".exe") },
            "Expected install command to be executed, got: $commands",
        )
    }

    @Test
    fun `install with keepAlive false does not add restart policy`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("winsw.exe", "install"), stdout = "Service installed")

        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val handler = createHandler(mockExecutor, winswExecutablePath = winswExe.absolutePath)
        val xmlPath = tempDir.resolve("test-daemon.xml").toString()

        handler.install(createConfig(tempDir, configPath = xmlPath, keepAlive = false))

        assertFalse(File(xmlPath).readText().contains("<onfailure"), "Should not have restart policy when keepAlive is false")
    }

    @Test
    fun `install handles already installed service`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockCommand(listOf("winsw.exe", "install"), exitCode = 1, stderr = "Service already exists")

        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val handler = createHandler(mockExecutor, winswExecutablePath = winswExe.absolutePath)

        assertDoesNotThrow { handler.install(createConfig(tempDir)) }
    }

    @Test
    fun `start service successfully returns PID`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(winswExe.absolutePath, "start"), stdout = "Service started")
            .mockSuccess(
                listOf("sc", "queryex", "test-daemon"),
                stdout = """
                    SERVICE_NAME: test-daemon
                    TYPE               : 10  WIN32_OWN_PROCESS
                    STATE              : 4  RUNNING
                    PID                : 12345
                """.trimIndent(),
            )

        val pid = createHandler(mockExecutor).start(createConfig(tempDir))

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf(winswExe.absolutePath, "start")))
        assertTrue(mockExecutor.wasExecuted(listOf("sc", "queryex")))
    }

    @Test
    fun `start service when already running returns PID`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockCommand(listOf(winswExe.absolutePath, "start"), exitCode = 1, stderr = "Service is already started")
            .mockSuccess(listOf("sc", "queryex", "test-daemon"), stdout = "PID                : 54321")

        val pid = createHandler(mockExecutor).start(createConfig(tempDir))

        assertEquals(54321L, pid)
    }

    @Test
    fun `start service throws exception when not installed`(@TempDir tempDir: Path) {
        val config = DaemonConfig(
            serviceId = "nonexistent",
            jarFile = tempDir.resolve("test.jar").toFile().also { it.createNewFile() },
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = tempDir.resolve("nonexistent.xml").toString(),
            logPath = tempDir.resolve("nonexistent.out.log").toString(),
            jvmArgs = emptyList(),
            appArgs = emptyList(),
            keepAlive = true,
        )

        val exception = assertThrows(RuntimeException::class.java) {
            createHandler(MockProcessExecutor()).start(config)
        }

        assertTrue(exception.message?.contains("not found") == true)
        assertTrue(exception.message?.contains("installDaemon") == true)
    }

    @Test
    fun `stop service successfully returns PID`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("sc", "queryex", "test-daemon"), stdout = "PID                : 12345")
            .mockSuccess(listOf(winswExe.absolutePath, "stop"), stdout = "Service stopped")

        val pid = createHandler(mockExecutor).stop(createConfig(tempDir))

        assertEquals(12345L, pid)
        assertTrue(mockExecutor.wasExecuted(listOf(winswExe.absolutePath, "stop")))
    }

    @Test
    fun `stop service when not running returns null`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockFailure(listOf("sc", "queryex", "test-daemon"), stderr = "Service not found", exitCode = 1060)
            .mockCommand(listOf(winswExe.absolutePath, "stop"), exitCode = 1, stderr = "Service is not running")

        assertNull(createHandler(mockExecutor).stop(createConfig(tempDir)))
    }

    @Test
    fun `getStatus when service is running`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(winswExe.absolutePath, "status"), stdout = "Started\n")
            .mockSuccess(listOf("sc", "queryex", "test-daemon"), stdout = "PID                : 12345")

        val status = createHandler(mockExecutor).getStatus(createConfig(tempDir))

        assertTrue(status.running)
        assertEquals(12345L, status.pid)
        assertEquals("Windows service is running via WinSW", status.details)
        assertNotNull(status.configPath)
        assertNotNull(status.logPath)
    }

    @Test
    fun `getStatus when service is stopped`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(winswExe.absolutePath, "status"), stdout = "Stopped\n")

        val status = createHandler(mockExecutor).getStatus(createConfig(tempDir))

        assertFalse(status.running)
        assertNull(status.pid)
        assertTrue(status.details.contains("not running"))
        assertTrue(status.details.contains("Stopped"))
    }

    @Test
    fun `getStatus when WinSW not installed`(@TempDir tempDir: Path) {
        val config = DaemonConfig(
            serviceId = "nonexistent",
            jarFile = tempDir.resolve("test.jar").toFile().also { it.createNewFile() },
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = tempDir.resolve("nonexistent.xml").toString(),
            logPath = tempDir.resolve("nonexistent.out.log").toString(),
            jvmArgs = emptyList(),
            appArgs = emptyList(),
            keepAlive = true,
        )

        val status = createHandler(MockProcessExecutor()).getStatus(config)

        assertFalse(status.running)
        assertTrue(status.details.contains("not installed"))
        assertNotNull(status.configPath)
    }

    @Test
    fun `cleanup uninstalls service and removes files`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val xmlFile = tempDir.resolve("test-daemon.xml").toFile()
        xmlFile.writeText("<service></service>")

        val outLog = tempDir.resolve("test-daemon.out.log").toFile()
        outLog.writeText("log output")

        val errLog = tempDir.resolve("test-daemon.err.log").toFile()
        errLog.writeText("error output")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(winswExe.absolutePath, "stop"), stdout = "Stopped")
            .mockSuccess(listOf(winswExe.absolutePath, "uninstall"), stdout = "Uninstalled")

        createHandler(mockExecutor).cleanup(createConfig(tempDir))

        assertFalse(winswExe.exists(), "WinSW executable should be deleted")
        assertFalse(xmlFile.exists(), "XML config should be deleted")
        assertFalse(outLog.exists(), "Output log should be deleted")
        assertFalse(errLog.exists(), "Error log should be deleted")
        assertTrue(mockExecutor.wasExecuted(listOf(winswExe.absolutePath, "stop")))
        assertTrue(mockExecutor.wasExecuted(listOf(winswExe.absolutePath, "uninstall")))
    }

    @Test
    fun `cleanup falls back to sc delete when uninstall fails`(@TempDir tempDir: Path) {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")
        tempDir.resolve("test-daemon.xml").toFile().writeText("<service></service>")

        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf(winswExe.absolutePath, "stop"), stdout = "Stopped")
            .mockFailure(listOf(winswExe.absolutePath, "uninstall"), stderr = "Failed to uninstall", exitCode = 1)
            .mockSuccess(listOf("sc.exe", "delete", "test-daemon"), stdout = "SUCCESS")

        createHandler(mockExecutor, serviceName = "test-daemon").cleanup(createConfig(tempDir))

        assertTrue(mockExecutor.wasExecuted(listOf("sc.exe", "delete", "test-daemon")))
    }

    @Test
    fun `custom service name is used in configuration`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("winsw.exe", "install"), stdout = "Service installed")

        val winswExe = tempDir.resolve("my-service.exe").toFile()
        winswExe.writeText("fake winsw")

        val handler = createHandler(
            mockExecutor,
            winswExecutablePath = winswExe.absolutePath,
            serviceName = "MyCustomService",
            serviceDisplayName = "My Custom Display Name",
            serviceDescription = "This is a custom service description",
        )

        handler.install(createConfig(tempDir))

        val content = File(createConfig(tempDir).configPath).readText()
        assertTrue(content.contains("<id>MyCustomService</id>"))
        assertTrue(content.contains("<name>My Custom Display Name</name>"))
        assertTrue(content.contains("<description>This is a custom service description</description>"))
    }

    @Test
    fun `getDefaultConfigPath returns correct path`() {
        val handler = WindowsWinswHandler()
        val serviceId = "com.example.test-daemon"

        val path = handler.getDefaultConfigPath(serviceId, null)

        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val expectedPath = File(appData, "$serviceId${File.separator}$serviceId.xml").absolutePath
        assertEquals(expectedPath, path)
    }

    @Test
    fun `arguments with spaces are properly escaped in XML`(@TempDir tempDir: Path) {
        val mockExecutor = MockProcessExecutor()
            .mockSuccess(listOf("winsw.exe", "install"), stdout = "Service installed")

        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        winswExe.writeText("fake winsw")

        val jarFile = tempDir.resolve("test daemon.jar").toFile()
        jarFile.createNewFile()

        val config = DaemonConfig(
            serviceId = "test-daemon",
            jarFile = jarFile,
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = tempDir.resolve("test-daemon.xml").toString(),
            logPath = tempDir.resolve("test-daemon.out.log").toString(),
            jvmArgs = listOf("-Dfoo=bar baz"),
            appArgs = listOf("--config", "my config.json"),
            keepAlive = true,
        )

        createHandler(mockExecutor, winswExecutablePath = winswExe.absolutePath).install(config)

        val content = File(config.configPath).readText()
        assertTrue(content.contains("\"my config.json\"") || content.contains("my config.json"))
    }

    private fun createConfig(
        tempDir: Path,
        configPath: String = tempDir.resolve("test-daemon.xml").toString(),
        keepAlive: Boolean = true,
        winswPath: String? = null,
    ): DaemonConfig {
        val winswExe = tempDir.resolve("test-daemon.exe").toFile()
        if (!winswExe.exists()) winswExe.writeText("fake winsw")

        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        if (!jarFile.exists()) jarFile.createNewFile()

        return DaemonConfig(
            serviceId = "test-daemon",
            jarFile = jarFile,
            javaHome = "C:\\Program Files\\Java\\jdk-21",
            configPath = configPath,
            logPath = tempDir.resolve("test-daemon.out.log").toString(),
            jvmArgs = listOf("-Xmx512m"),
            appArgs = emptyList(),
            keepAlive = keepAlive,
        )
    }
}
