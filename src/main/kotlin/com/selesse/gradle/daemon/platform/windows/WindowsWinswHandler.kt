package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.process.ProcessExecutor
import com.selesse.gradle.daemon.process.Processes
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Windows handler that uses WinSW (Windows Service Wrapper) to manage the daemon as a proper Windows service.
 * This is the recommended approach for production deployments on Windows.
 *
 * WinSW: https://github.com/winsw/winsw
 */
class WindowsWinswHandler(
    private val processExecutor: ProcessExecutor = Processes(),
    private val winswVersion: String = "3.0.0-alpha.11",
    private val winswExecutablePath: String? = null,
    private val serviceName: String? = null,
    private val serviceDisplayName: String? = null,
    private val serviceDescription: String? = null,
) : DaemonBackend {
    private val logger = Logging.getLogger(WindowsWinswHandler::class.java)

    companion object {
        private const val DEFAULT_WINSW_VERSION = "3.0.0-alpha.11"

        fun getDownloadUrl(version: String): String {
            return "https://github.com/winsw/winsw/releases/download/v$version/WinSW-x64.exe"
        }
    }

    override fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String {
        // WinSW uses XML config file with same name as exe in release directory
        val releaseDir = getReleaseDir(serviceId, platformConfig)
        return File(releaseDir, "$serviceId.xml").absolutePath
    }

    override fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String {
        // WinSW automatically creates .out.log and .err.log files
        val serviceId = extension.serviceId.get()
        val releaseDir = getReleaseDir(serviceId, extension.windows)
        return File(releaseDir, "$serviceId.out.log").absolutePath
    }

    override fun install(config: DaemonConfig) {
        val releaseDir = File(config.configPath).parentFile
        releaseDir.mkdirs()

        // Get or download WinSW executable
        val winswExe = getWinSWExecutable(config.serviceId, releaseDir)
        logger.lifecycle("Using WinSW executable: ${winswExe.absolutePath}")

        // Generate XML configuration
        val xmlConfig = generateWinSWConfig(config)
        val xmlFile = File(config.configPath)
        xmlFile.writeText(xmlConfig)
        logger.lifecycle("Generated WinSW configuration: ${xmlFile.absolutePath}")

        // Install the service (WinSW automatically finds the XML file with matching name)
        val result = processExecutor.execute(listOf(winswExe.absolutePath, "install"))
        if (result.exitCode == 0) {
            logger.lifecycle("Successfully installed Windows service via WinSW")
        } else {
            // Service might already be installed, that's okay
            if (result.stderr.contains("already exists") || result.stdout.contains("already exists")) {
                logger.lifecycle("Windows service already installed, updating configuration")
            } else {
                logger.warn("WinSW install output: ${result.stdout}")
                logger.warn("WinSW install errors: ${result.stderr}")
            }
        }
    }

    override fun start(config: DaemonConfig): Long? {
        val releaseDir = File(config.configPath).parentFile
        val winswExe = File(releaseDir, "${config.serviceId}.exe")

        if (!winswExe.exists()) {
            throw RuntimeException("WinSW executable not found at ${winswExe.absolutePath}. Please run installDaemon first.")
        }

        val result = processExecutor.execute(listOf(winswExe.absolutePath, "start"))

        if (result.exitCode == 0) {
            logger.lifecycle("Started Windows service via WinSW")
            // Try to get the PID
            Thread.sleep(500) // Give service time to start
            return getServicePid(config.serviceId)
        } else {
            if (result.stderr.contains("already started") || result.stdout.contains("already started")) {
                logger.lifecycle("Windows service is already running")
                return getServicePid(config.serviceId)
            } else {
                throw RuntimeException("Failed to start service: ${result.stderr}")
            }
        }
    }

    override fun stop(config: DaemonConfig): Long? {
        val releaseDir = File(config.configPath).parentFile
        val winswExe = File(releaseDir, "${config.serviceId}.exe")

        if (!winswExe.exists()) {
            logger.warn("WinSW executable not found at ${winswExe.absolutePath}")
            return null
        }

        val pid = getServicePid(config.serviceId)
        val result = processExecutor.execute(listOf(winswExe.absolutePath, "stop"))

        if (result.exitCode == 0) {
            logger.lifecycle("Stopped Windows service via WinSW")
            return pid
        } else {
            if (result.stderr.contains("not running") || result.stdout.contains("not running")) {
                logger.lifecycle("Windows service is not running")
                return null
            } else {
                logger.warn("Failed to stop service: ${result.stderr}")
                return null
            }
        }
    }

    override fun getStatus(config: DaemonConfig): DaemonStatus {
        val releaseDir = File(config.configPath).parentFile
        val winswExe = File(releaseDir, "${config.serviceId}.exe")
        val xmlFile = File(config.configPath)

        if (!winswExe.exists()) {
            return DaemonStatus(
                running = false,
                details = "WinSW not installed (executable not found at ${winswExe.absolutePath})",
                configPath = config.configPath,
                logPath = config.logPath,
            )
        }

        val result = processExecutor.execute(listOf(winswExe.absolutePath, "status"))
        // WinSW returns "Started" when running, "Stopped" when not running
        val isRunning = result.exitCode == 0 && result.stdout.trim().equals("Started", ignoreCase = true)

        return if (isRunning) {
            val pid = getServicePid(config.serviceId)
            DaemonStatus(
                running = true,
                pid = pid,
                details = "Windows service is running via WinSW",
                configPath = xmlFile.absolutePath,
                logPath = config.logPath,
            )
        } else {
            DaemonStatus(
                running = false,
                details = "Windows service is not running (status: ${result.stdout.trim()})",
                configPath = xmlFile.absolutePath,
                logPath = config.logPath,
            )
        }
    }

    override fun cleanup(config: DaemonConfig) {
        val releaseDir = File(config.configPath).parentFile
        val winswExe = File(releaseDir, "${config.serviceId}.exe")
        val xmlFile = File(config.configPath)

        if (winswExe.exists()) {
            // Stop the service first
            logger.lifecycle("Stopping service before uninstall...")
            processExecutor.execute(listOf(winswExe.absolutePath, "stop"))

            // Wait for service to fully stop
            Thread.sleep(2000)

            // Uninstall the service
            val result = processExecutor.execute(listOf(winswExe.absolutePath, "uninstall"))

            if (result.exitCode == 0) {
                logger.lifecycle("Uninstalled Windows service via WinSW")
            } else {
                logger.warn("WinSW uninstall exit code: ${result.exitCode}")
                logger.warn("Output: ${result.stdout}")
                logger.warn("Errors: ${result.stderr}")

                // Try to forcefully remove service using sc.exe
                val actualServiceName = serviceName ?: config.serviceId
                val scResult = processExecutor.execute(listOf("sc.exe", "delete", actualServiceName))
                if (scResult.exitCode == 0) {
                    logger.lifecycle("Deleted service via sc.exe")
                } else {
                    logger.warn("Failed to delete service via sc.exe: ${scResult.stderr}")
                }
            }

            // Clean up WinSW executable
            if (winswExe.delete()) {
                logger.lifecycle("Removed WinSW executable: ${winswExe.absolutePath}")
            } else {
                logger.warn("Failed to delete WinSW executable, it may be in use")
            }

            // Clean up log files
            val outLog = File(releaseDir, "${config.serviceId}.out.log")
            val errLog = File(releaseDir, "${config.serviceId}.err.log")
            val wrapperLog = File(releaseDir, "${config.serviceId}.wrapper.log")

            listOf(outLog, errLog, wrapperLog).forEach { logFile ->
                if (logFile.exists() && logFile.delete()) {
                    logger.lifecycle("Removed log file: ${logFile.name}")
                }
            }
        }

        // Remove XML configuration
        if (xmlFile.exists()) {
            if (xmlFile.delete()) {
                logger.lifecycle("Removed WinSW configuration: ${xmlFile.absolutePath}")
            }
        }
    }

    private fun getReleaseDir(serviceId: String, platformConfig: Any?): File {
        // Default to APPDATA/serviceId
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return File(appData, serviceId)
    }

    private fun generateWinSWConfig(config: DaemonConfig): String {
        val actualServiceName = serviceName ?: config.serviceId
        val actualDisplayName = serviceDisplayName ?: config.serviceId
        val actualDescription = serviceDescription ?: "${config.serviceId} Daemon Service"
        val javaExe = File(config.javaHome, "bin\\java.exe").absolutePath

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<service>")
            appendLine("  <id>$actualServiceName</id>")
            appendLine("  <name>$actualDisplayName</name>")
            appendLine("  <description>$actualDescription</description>")
            appendLine("  <executable>$javaExe</executable>")

            // Build arguments - all arguments must be on a single line for WinSW
            val allArgs = buildList {
                addAll(config.jvmArgs)
                add("-jar")
                add(config.jarFile.absolutePath)
                addAll(config.appArgs)
            }.joinToString(" ") { arg ->
                // Escape arguments that contain spaces
                if (arg.contains(" ")) "\"$arg\"" else arg
            }
            appendLine("  <arguments>$allArgs</arguments>")

            // Log configuration
            appendLine("  <log mode=\"roll\">")
            appendLine("  </log>")

            // Service recovery options
            if (config.keepAlive) {
                appendLine("  <onfailure action=\"restart\" delay=\"10 sec\"/>")
                appendLine("  <onfailure action=\"restart\" delay=\"20 sec\"/>")
                appendLine("  <resetfailure>1 day</resetfailure>")
            }

            appendLine("</service>")
        }
    }

    private fun getWinSWExecutable(serviceId: String, releaseDir: File): File {
        // If user provided a path, use that
        winswExecutablePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                // Copy to release directory with service ID name
                val targetFile = File(releaseDir, "$serviceId.exe")
                if (!targetFile.exists() || file.absolutePath != targetFile.absolutePath) {
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                return targetFile
            } else {
                logger.warn("Specified WinSW executable not found: $path, will download instead")
            }
        }

        // Otherwise, download to release directory with service ID as name
        val winswFile = File(releaseDir, "$serviceId.exe")

        if (!winswFile.exists()) {
            val downloadUrl = getDownloadUrl(winswVersion)
            logger.lifecycle("Downloading WinSW from $downloadUrl...")
            try {
                URL(downloadUrl).openStream().use { input ->
                    Files.copy(input, winswFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                logger.lifecycle("Downloaded WinSW to: ${winswFile.absolutePath}")
            } catch (e: Exception) {
                throw RuntimeException("Failed to download WinSW: ${e.message}", e)
            }
        }

        return winswFile
    }

    private fun getServicePid(serviceId: String): Long? {
        val actualServiceName = serviceName ?: serviceId

        // Use sc query to get service PID
        val result = processExecutor.execute(listOf("sc", "queryex", actualServiceName))

        if (result.exitCode == 0) {
            // Parse PID from output like "PID                : 1234"
            val pidPattern = Regex("""PID\s+:\s+(\d+)""")
            val match = pidPattern.find(result.stdout)
            return match?.groupValues?.get(1)?.toLongOrNull()
        }

        return null
    }
}
