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

class WindowsStartupHandler(
    private val processExecutor: ProcessExecutor = Processes(),
    private val useStartupFolder: Boolean = true,
) : DaemonBackend {
    private val logger = Logging.getLogger(WindowsStartupHandler::class.java)

    override fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String {
        // Windows startup folder doesn't use a config file
        return ""
    }

    override fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String {
        // Windows startup folder doesn't have a standard log path
        return ""
    }

    override fun install(config: DaemonConfig) {
        if (useStartupFolder) {
            val startupFolder = File(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            startupFolder.mkdirs()

            val destinationFile = File(startupFolder, config.jarFile.name)
            config.jarFile.copyTo(destinationFile, overwrite = true)
            logger.lifecycle("Installed JAR to Windows startup folder: {}", destinationFile.absolutePath)
        }
    }

    override fun start(config: DaemonConfig): Long? {
        val javawExe = File(config.javaHome, "bin\\javaw.exe")

        val command = buildList {
            add(javawExe.absolutePath)
            addAll(config.jvmArgs)
            add("-jar")
            add(config.jarFile.absolutePath)
            addAll(config.appArgs)
        }

        val process = ProcessBuilder(command).start()
        return process.pid()
    }

    override fun stop(config: DaemonConfig): Long? {
        val pid = findDaemonPid(config.jarFile)

        if (pid != null) {
            val result = processExecutor.execute(listOf("taskkill", "/PID", pid.toString(), "/F"))
            if (result.exitCode == 0) {
                return pid
            } else {
                logger.warn("Failed to kill daemon with PID {}: {}", pid, result.stderr)
            }
        }

        return null
    }

    override fun getStatus(config: DaemonConfig): DaemonStatus {
        val pid = findDaemonPid(config.jarFile)

        return if (pid != null) {
            DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running",
                configPath = null,
                logPath = null,
            )
        } else {
            DaemonStatus(
                running = false,
                details = "Daemon is not running",
                configPath = null,
                logPath = null,
            )
        }
    }

    override fun cleanup(config: DaemonConfig) {
        if (useStartupFolder) {
            val startupFolder = File(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            val startupJar = File(startupFolder, config.jarFile.name)
            if (startupJar.exists()) {
                startupJar.delete()
                logger.lifecycle("Removed JAR from startup folder")
            }
        }
    }

    private fun findDaemonPid(jarFile: File): Long? {
        val result = processExecutor.execute(
            listOf("wmic", "process", "where", "name='javaw.exe'", "get", "CommandLine,ProcessId"),
        )

        if (result.exitCode != 0) {
            return null
        }

        val jarName = jarFile.name
        val lines = result.stdout.lines()

        for (line in lines) {
            if (line.contains(jarName)) {
                val parts = line.trim().split(Regex("\\s+"))
                val pidString = parts.lastOrNull()
                return pidString?.toLongOrNull()
            }
        }

        return null
    }
}
