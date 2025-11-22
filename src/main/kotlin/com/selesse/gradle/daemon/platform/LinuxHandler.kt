package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

class LinuxHandler : PlatformHandler {
    override fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
        logger: Logger,
    ) {
        val servicePath = getServicePath(extension)
        val serviceFile = File(servicePath)

        // Ensure parent directory exists
        serviceFile.parentFile.mkdirs()

        val serviceContent = generateSystemdService(extension, jarFile, javaHome)
        serviceFile.writeText(serviceContent)

        logger.lifecycle("Installed systemd service to: $servicePath")

        // Reload systemd daemon to recognize new service
        val reloadResult = executeCommand(listOf("systemctl", "--user", "daemon-reload"))
        if (reloadResult.exitCode == 0) {
            logger.lifecycle("Reloaded systemd daemon")
        }

        // Enable service to start on boot
        val enableResult = executeCommand(
            listOf("systemctl", "--user", "enable", getServiceName(extension)),
        )
        if (enableResult.exitCode == 0) {
            logger.lifecycle("Enabled service to start on boot")
        }
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val serviceName = getServiceName(extension)

        val result = executeCommand(listOf("systemctl", "--user", "start", serviceName))
        if (result.exitCode == 0) {
            logger.lifecycle("Started daemon via systemctl")
            return getPid(serviceName)
        } else {
            throw RuntimeException("Failed to start daemon: ${result.stderr}")
        }
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val serviceName = getServiceName(extension)
        val pid = getPid(serviceName)

        val result = executeCommand(listOf("systemctl", "--user", "stop", serviceName))
        if (result.exitCode == 0) {
            logger.lifecycle("Stopped daemon via systemctl")
            return pid
        } else {
            logger.warn("Failed to stop daemon: ${result.stderr}")
            return null
        }
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): DaemonStatus {
        val serviceName = getServiceName(extension)
        val servicePath = getServicePath(extension)
        val logPath = getLogPath(project, extension)

        val result = executeCommand(listOf("systemctl", "--user", "is-active", serviceName))
        val isRunning = result.stdout.trim() == "active"

        if (isRunning) {
            val pid = getPid(serviceName)
            return DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running as systemd service",
                configPath = servicePath,
                logPath = logPath,
            )
        } else {
            return DaemonStatus(
                running = false,
                details = "Daemon is not running (status: ${result.stdout.trim()})",
                configPath = servicePath,
                logPath = logPath,
            )
        }
    }

    private fun getServicePath(extension: DaemonAppExtension): String {
        return extension.linux.servicePath
            ?: "${System.getProperty("user.home")}/.config/systemd/user/${getServiceName(extension)}"
    }

    private fun getServiceName(extension: DaemonAppExtension): String {
        return "${extension.serviceId.get()}.service"
    }

    private fun getLogPath(project: Project, extension: DaemonAppExtension): String {
        return extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: project.layout.projectDirectory.file("release/daemon.log").asFile.absolutePath
    }

    private fun getPid(serviceName: String): Long? {
        val result = executeCommand(
            listOf("systemctl", "--user", "show", "--property=MainPID", serviceName),
        )

        if (result.exitCode == 0) {
            // Output format: "MainPID=12345"
            val pidString = result.stdout.trim().substringAfter("MainPID=")
            return pidString.toLongOrNull()
        }

        return null
    }

    private fun generateSystemdService(
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
    ): String {
        val javaExecutable = "$javaHome/bin/java"
        val logFile = extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: jarFile.parentFile.resolve("daemon.log").absolutePath

        val execStartCommand = buildList {
            add(javaExecutable)
            addAll(extension.jvmArgs.getOrElse(emptyList()))
            add("-jar")
            add(jarFile.absolutePath)
            addAll(extension.appArgs.getOrElse(emptyList()))
        }.joinToString(" ")

        val restart = if (extension.keepAlive.getOrElse(true)) "always" else "no"

        return """
            |[Unit]
            |Description=${extension.serviceId.get()} Daemon
            |After=network.target
            |
            |[Service]
            |Type=simple
            |ExecStart=$execStartCommand
            |Restart=$restart
            |RestartSec=10
            |StandardOutput=append:$logFile
            |StandardError=append:$logFile
            |
            |[Install]
            |WantedBy=default.target
        """.trimMargin()
    }

    private fun executeCommand(command: List<String>): CommandResult {
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout, stderr)
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
