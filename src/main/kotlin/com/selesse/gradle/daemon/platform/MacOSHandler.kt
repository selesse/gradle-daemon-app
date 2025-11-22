package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

class MacOSHandler : PlatformHandler {
    override fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
        logger: Logger,
    ) {
        val plistPath = getPlistPath(extension)
        val plistFile = File(plistPath)

        plistFile.parentFile.mkdirs()

        val plistContent = generatePlist(extension, jarFile, javaHome)
        plistFile.writeText(plistContent)

        logger.lifecycle("Installed LaunchAgent plist to: $plistPath")
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val plistPath = getPlistPath(extension)
        val serviceId = extension.serviceId.get()

        val result = executeCommand(listOf("launchctl", "load", plistPath))
        if (result.exitCode == 0) {
            logger.lifecycle("Started daemon via launchctl load")

            Thread.sleep(500)

            val listResult = executeCommand(listOf("launchctl", "list"))
            val pidLine = listResult.stdout.lines().find { it.contains(serviceId) }
            val pid = pidLine?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()

            return pid
        } else {
            throw RuntimeException("Failed to start daemon: ${result.stderr}")
        }
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val plistPath = getPlistPath(extension)

        val result = executeCommand(listOf("launchctl", "unload", plistPath))
        if (result.exitCode == 0 || result.exitCode == 3) {
            logger.lifecycle("Stopped daemon via launchctl unload")
            return null
        } else {
            logger.warn("Failed to stop daemon (might not be running): ${result.stderr}")
            return null
        }
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): DaemonStatus {
        val serviceId = extension.serviceId.get()
        val plistPath = getPlistPath(extension)
        val logPath = getLogPath(project, extension)

        val result = executeCommand(listOf("launchctl", "list"))
        val isRunning = result.stdout.lines().any { it.contains(serviceId) }

        if (isRunning) {
            val pidLine = result.stdout.lines().find { it.contains(serviceId) }
            val pid = pidLine?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()
            return DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running as LaunchAgent",
                configPath = plistPath,
                logPath = logPath,
            )
        } else {
            return DaemonStatus(
                running = false,
                details = "Daemon is not running",
                configPath = plistPath,
                logPath = logPath,
            )
        }
    }

    private fun getPlistPath(extension: DaemonAppExtension): String {
        return extension.macOS.plistPath
            ?: "${System.getProperty("user.home")}/Library/LaunchAgents/${extension.serviceId.get()}.plist"
    }

    private fun getLogPath(project: Project, extension: DaemonAppExtension): String {
        return extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: project.layout.projectDirectory.file("release/daemon.log").asFile.absolutePath
    }

    private fun generatePlist(
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
    ): String {
        val serviceId = extension.serviceId.get()
        val javaExecutable = "$javaHome/bin/java"
        val logFile = extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: jarFile.parentFile.resolve("daemon.log").absolutePath

        val programArguments = buildList {
            add(javaExecutable)
            addAll(extension.jvmArgs.getOrElse(emptyList()))
            add("-jar")
            add(jarFile.absolutePath)
            addAll(extension.appArgs.getOrElse(emptyList()))
        }

        val keepAlive = extension.keepAlive.getOrElse(true)

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$serviceId</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |${programArguments.joinToString("\n") { "        <string>$it</string>" }}
            |    </array>
            |    <key>StandardOutPath</key>
            |    <string>$logFile</string>
            |    <key>StandardErrorPath</key>
            |    <string>$logFile</string>
            |    <key>KeepAlive</key>
            |    <${if (keepAlive) "true" else "false"}/>
            |</dict>
            |</plist>
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
