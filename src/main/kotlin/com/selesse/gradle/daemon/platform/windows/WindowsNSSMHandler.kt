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

/**
 * Windows NSSM (Non-Sucking Service Manager) backend for running the daemon as a Windows service.
 *
 * NSSM allows any executable to be run as a Windows service with proper lifecycle management,
 * automatic restart on failure, and integration with Windows Service Control Manager.
 *
 * If NSSM is not found in PATH and no custom path is provided, it will be automatically
 * downloaded to %APPDATA%\gradle-daemon-app\nssm\.
 *
 * **Important:** This backend requires administrator privileges to install, start, stop,
 * and remove Windows services. Run Gradle with elevated permissions (Run as Administrator).
 *
 * @see <a href="https://nssm.cc">NSSM Homepage</a>
 */
class WindowsNSSMHandler(
    private val processExecutor: ProcessExecutor = Processes(),
    private val nssmPathOverride: String? = null,
    private val nssmProvider: NSSMProvider = NSSMProvider(),
    private val skipAdminCheck: Boolean = false,
) : DaemonBackend {
    private val logger = Logging.getLogger(WindowsNSSMHandler::class.java)

    /**
     * Checks if the current process has administrator privileges.
     * Uses "net session" command which only succeeds with admin rights.
     */
    private fun requireAdminPrivileges(operation: String) {
        if (skipAdminCheck) return

        val result = processExecutor.execute(listOf("net", "session"))
        if (result.exitCode != 0) {
            throw RuntimeException(
                "Administrator privileges required to $operation. " +
                    "Please run Gradle as Administrator (right-click Command Prompt or Terminal → 'Run as administrator').",
            )
        }
    }

    private fun getNssmPath(platformConfig: Any?): String {
        // First check for explicit override
        if (nssmPathOverride != null) {
            return nssmPathOverride
        }

        // Check config for custom path
        if (platformConfig is DaemonAppExtension.WindowsConfig && platformConfig.nssmPath != null) {
            return platformConfig.nssmPath!!
        }

        // Use auto-downloaded NSSM
        return nssmProvider.getNssmPath().absolutePath
    }

    override fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String {
        // NSSM doesn't use a config file - configuration is stored in the Windows registry
        return ""
    }

    override fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val serviceId = extension.serviceId.get()
        return File(appData, "$serviceId\\daemon.log").absolutePath
    }

    override fun install(config: DaemonConfig) {
        requireAdminPrivileges("install Windows service")

        val nssmPath = getNssmPath(null)
        val serviceName = sanitizeServiceName(config.serviceId)
        val javaExe = File(config.javaHome, "bin\\java.exe")

        // Install the service with the Java executable
        val installResult = processExecutor.execute(
            listOf(nssmPath, "install", serviceName, javaExe.absolutePath),
        )

        if (installResult.exitCode != 0) {
            throw RuntimeException("Failed to install NSSM service: ${installResult.stderr}")
        }

        // Configure AppDirectory (working directory)
        val appDir = config.jarFile.parentFile.absolutePath
        processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppDirectory", appDir))

        // Configure application parameters (JVM args + -jar + jar file + app args)
        val appParameters = buildList {
            addAll(config.jvmArgs)
            add("-jar")
            add(config.jarFile.absolutePath)
            addAll(config.appArgs)
        }.joinToString(" ")
        processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppParameters", appParameters))

        // Configure logging if log path is set
        if (config.logPath.isNotEmpty()) {
            val logFile = File(config.logPath)
            logFile.parentFile?.mkdirs()
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppStdout", config.logPath))
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppStderr", config.logPath))
            // Append to log files instead of overwriting
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppStdoutCreationDisposition", "4"))
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppStderrCreationDisposition", "4"))
        }

        // Configure restart behavior based on keepAlive
        if (config.keepAlive) {
            // Restart on exit (exit action: Restart)
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppExit", "Default", "Restart"))
            // Delay 5 seconds before restart
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppRestartDelay", "5000"))
        } else {
            // Exit on application exit
            processExecutor.execute(listOf(nssmPath, "set", serviceName, "AppExit", "Default", "Exit"))
        }

        // Set service to auto-start
        processExecutor.execute(listOf(nssmPath, "set", serviceName, "Start", "SERVICE_AUTO_START"))

        // Set display name and description
        processExecutor.execute(listOf(nssmPath, "set", serviceName, "DisplayName", config.serviceId))
        processExecutor.execute(
            listOf(nssmPath, "set", serviceName, "Description", "Daemon service managed by gradle-daemon-app"),
        )

        logger.lifecycle("Installed NSSM service: {}", serviceName)
    }

    override fun start(config: DaemonConfig): Long? {
        requireAdminPrivileges("start Windows service")

        val nssmPath = getNssmPath(null)
        val serviceName = sanitizeServiceName(config.serviceId)

        val result = processExecutor.execute(listOf(nssmPath, "start", serviceName))

        if (result.exitCode != 0) {
            logger.warn("Failed to start NSSM service: {}", result.stderr)
            return null
        }

        logger.lifecycle("Started NSSM service: {}", serviceName)

        // Try to get the PID after starting
        return getServicePid(serviceName, nssmPath)
    }

    override fun stop(config: DaemonConfig): Long? {
        requireAdminPrivileges("stop Windows service")

        val nssmPath = getNssmPath(null)
        val serviceName = sanitizeServiceName(config.serviceId)

        // Get PID before stopping
        val pid = getServicePid(serviceName, nssmPath)

        val result = processExecutor.execute(listOf(nssmPath, "stop", serviceName))

        if (result.exitCode != 0) {
            logger.warn("Failed to stop NSSM service: {}", result.stderr)
            return null
        }

        logger.lifecycle("Stopped NSSM service: {}", serviceName)
        return pid
    }

    override fun getStatus(config: DaemonConfig): DaemonStatus {
        val nssmPath = getNssmPath(null)
        val serviceName = sanitizeServiceName(config.serviceId)

        val result = processExecutor.execute(listOf(nssmPath, "status", serviceName))

        // NSSM status output examples:
        // SERVICE_RUNNING - service is running
        // SERVICE_STOPPED - service is stopped
        // SERVICE_PAUSED - service is paused
        // Service XXX doesn't exist - service not installed
        val statusOutput = result.stdout.trim()

        val isRunning = statusOutput.contains("SERVICE_RUNNING")
        val isStopped = statusOutput.contains("SERVICE_STOPPED") ||
            statusOutput.contains("SERVICE_PAUSED") ||
            statusOutput.contains("doesn't exist", ignoreCase = true)

        val details = when {
            isRunning -> "Service is running"
            statusOutput.contains("doesn't exist", ignoreCase = true) -> "Service is not installed"
            isStopped -> "Service is stopped"
            result.exitCode != 0 -> "Failed to get status: ${result.stderr}"
            else -> "Status: $statusOutput"
        }

        val pid = if (isRunning) getServicePid(serviceName, nssmPath) else null

        return DaemonStatus(
            running = isRunning,
            pid = pid,
            details = details,
            // NSSM stores config in registry
            configPath = null,
            logPath = config.logPath.ifEmpty { null },
        )
    }

    override fun cleanup(config: DaemonConfig) {
        requireAdminPrivileges("remove Windows service")

        val nssmPath = getNssmPath(null)
        val serviceName = sanitizeServiceName(config.serviceId)

        // Remove the service (confirm flag bypasses interactive prompt)
        val result = processExecutor.execute(listOf(nssmPath, "remove", serviceName, "confirm"))

        if (result.exitCode != 0) {
            // Don't fail if service doesn't exist
            if (!result.stderr.contains("doesn't exist", ignoreCase = true) &&
                !result.stdout.contains("doesn't exist", ignoreCase = true)
            ) {
                logger.warn("Failed to remove NSSM service: {}", result.stderr)
            }
        } else {
            logger.lifecycle("Removed NSSM service: {}", serviceName)
        }
    }

    /**
     * Sanitize service ID to a valid Windows service name.
     * Windows service names cannot contain certain characters.
     */
    private fun sanitizeServiceName(serviceId: String): String {
        // Replace dots with underscores and remove invalid characters
        return serviceId.replace(".", "_").replace(Regex("[^a-zA-Z0-9_-]"), "")
    }

    /**
     * Get the PID of the running service process.
     */
    private fun getServicePid(serviceName: String, nssmPath: String): Long? {
        // Query the service PID using NSSM
        val result = processExecutor.execute(listOf(nssmPath, "get", serviceName, "ObjectName"))

        // NSSM doesn't directly expose PID, so we query via sc/tasklist
        val scResult = processExecutor.execute(listOf("sc", "queryex", serviceName))
        if (scResult.exitCode != 0) {
            return null
        }

        // Parse PID from sc queryex output
        // Format: "PID                : 12345"
        val pidMatch = Regex("""PID\s*:\s*(\d+)""").find(scResult.stdout)
        return pidMatch?.groupValues?.get(1)?.toLongOrNull()
    }
}
