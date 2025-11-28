package com.selesse.gradle.daemon.platform.linux

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.process.ProcessExecutor
import com.selesse.gradle.daemon.process.Processes
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import java.io.File

class LinuxSystemDHandler(
    private val processExecutor: ProcessExecutor = Processes(),
) : DaemonBackend {
    private val logger = Logging.getLogger(LinuxSystemDHandler::class.java)

    override fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String {
        val linuxConfig = platformConfig as? DaemonAppExtension.LinuxConfig
        val serviceName = "$serviceId.service"
        return linuxConfig?.servicePath
            ?: "${System.getProperty("user.home")}/.config/systemd/user/$serviceName"
    }

    override fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String {
        return extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: project.layout.projectDirectory.file("release/daemon.log").asFile.absolutePath
    }

    override fun install(config: DaemonConfig) {
        val serviceFile = File(config.configPath)
        serviceFile.parentFile.mkdirs()

        val serviceContent = generateSystemdService(config)
        serviceFile.writeText(serviceContent)
        logger.lifecycle("Wrote systemd service to {}", serviceFile.absolutePath)

        // Reload systemd daemon to recognize new service
        processExecutor.execute(listOf("systemctl", "--user", "daemon-reload"))

        // Enable service to start on boot
        val serviceName = getServiceName(config.serviceId)
        processExecutor.execute(listOf("systemctl", "--user", "enable", serviceName))
    }

    override fun start(config: DaemonConfig): Long? {
        val serviceName = getServiceName(config.serviceId)

        val result = processExecutor.execute(listOf("systemctl", "--user", "start", serviceName))
        if (result.exitCode == 0) {
            return getPid(serviceName)
        } else {
            throw RuntimeException("Failed to start daemon: ${result.stderr}")
        }
    }

    override fun stop(config: DaemonConfig): Long? {
        val serviceName = getServiceName(config.serviceId)
        val pid = getPid(serviceName)

        val result = processExecutor.execute(listOf("systemctl", "--user", "stop", serviceName))
        if (result.exitCode == 0) {
            return pid
        } else {
            logger.warn("Failed to stop daemon: {}", result.stderr)
            return null
        }
    }

    override fun getStatus(config: DaemonConfig): DaemonStatus {
        val serviceName = getServiceName(config.serviceId)

        val result = processExecutor.execute(listOf("systemctl", "--user", "is-active", serviceName))
        val isRunning = result.stdout.trim() == "active"

        if (isRunning) {
            val pid = getPid(serviceName)
            return DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running as systemd service",
                configPath = config.configPath,
                logPath = config.logPath,
            )
        } else {
            return DaemonStatus(
                running = false,
                details = "Daemon is not running (status: ${result.stdout.trim()})",
                configPath = config.configPath,
                logPath = config.logPath,
            )
        }
    }

    override fun cleanup(config: DaemonConfig) {
        val serviceName = getServiceName(config.serviceId)

        processExecutor.execute(listOf("systemctl", "--user", "disable", serviceName))

        val configFile = File(config.configPath)
        if (configFile.exists()) {
            configFile.delete()
        }

        processExecutor.execute(listOf("systemctl", "--user", "daemon-reload"))
    }

    private fun getServiceName(serviceId: String): String {
        return "$serviceId.service"
    }

    private fun getPid(serviceName: String): Long? {
        val result = processExecutor.execute(
            listOf("systemctl", "--user", "show", "--property=MainPID", serviceName),
        )

        if (result.exitCode == 0) {
            val pidString = result.stdout.trim().substringAfter("MainPID=")
            return pidString.toLongOrNull()
        }

        return null
    }

    private fun generateSystemdService(config: DaemonConfig): String {
        val javaExecutable = "${config.javaHome}/bin/java"

        val execStartCommand = buildList {
            add(javaExecutable)
            addAll(config.jvmArgs)
            add("-jar")
            add(config.jarFile.absolutePath)
            addAll(config.appArgs)
        }.joinToString(" ")

        val restart = if (config.keepAlive) "always" else "no"

        return """
            |[Unit]
            |Description=${config.serviceId} Daemon
            |After=network.target
            |
            |[Service]
            |Type=simple
            |ExecStart=$execStartCommand
            |Restart=$restart
            |RestartSec=10
            |StandardOutput=append:${config.logPath}
            |StandardError=append:${config.logPath}
            |
            |[Install]
            |WantedBy=default.target
        """.trimMargin()
    }
}
