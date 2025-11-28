package com.selesse.gradle.daemon.platform.macos

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.process.ProcessExecutor
import com.selesse.gradle.daemon.process.Processes
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import java.io.File

class MacOSPlistHandler(
    private val processExecutor: ProcessExecutor = Processes(),
) : DaemonBackend {
    private val logger = Logging.getLogger(MacOSPlistHandler::class.java)

    override fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String {
        val macOSConfig = platformConfig as? DaemonAppExtension.MacOSConfig
        return macOSConfig?.plistPath
            ?: "${System.getProperty("user.home")}/Library/LaunchAgents/$serviceId.plist"
    }

    override fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String {
        return extension.logFile.orNull?.asFile?.absolutePath
            ?: extension.releaseDir.orNull?.asFile?.resolve("daemon.log")?.absolutePath
            ?: project.layout.projectDirectory.file("release/daemon.log").asFile.absolutePath
    }

    override fun install(config: DaemonConfig) {
        val plistFile = File(config.configPath)
        plistFile.parentFile.mkdirs()

        logger.lifecycle("Writing plist to: {}", plistFile)
        val plistContent = generatePlist(config)
        plistFile.writeText(plistContent)
    }

    override fun start(config: DaemonConfig): Long? {
        val result = processExecutor.execute(listOf("launchctl", "load", config.configPath))
        if (result.exitCode == 0) {
            Thread.sleep(500)

            val listResult = processExecutor.execute(listOf("launchctl", "list"))
            val pidLine = listResult.stdout.lines().find { it.contains(config.serviceId) }
            val pid = pidLine?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()

            return pid
        } else {
            throw RuntimeException("Failed to start daemon: ${result.stderr}")
        }
    }

    override fun stop(config: DaemonConfig): Long? {
        val result = processExecutor.execute(listOf("launchctl", "unload", config.configPath))
        if (result.exitCode == 0 || result.exitCode == 3) {
            // 0 -> success, 3 -> service not found, which we also consider a success
        } else {
            logger.warn("Failed to stop daemon: {}", result.stderr)
        }
        return null
    }

    override fun getStatus(config: DaemonConfig): DaemonStatus {
        val result = processExecutor.execute(listOf("launchctl", "list"))
        val isRunning = result.stdout.lines().any { it.contains(config.serviceId) }

        if (isRunning) {
            val pidLine = result.stdout.lines().find { it.contains(config.serviceId) }
            val pid = pidLine?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()
            return DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running as LaunchAgent",
                configPath = config.configPath,
                logPath = config.logPath,
            )
        } else {
            return DaemonStatus(
                running = false,
                details = "Daemon is not running",
                configPath = config.configPath,
                logPath = config.logPath,
            )
        }
    }

    override fun cleanup(config: DaemonConfig) {
        val configFile = File(config.configPath)
        if (configFile.exists()) {
            configFile.delete()
        }
    }

    private fun generatePlist(config: DaemonConfig): String {
        val javaExecutable = "${config.javaHome}/bin/java"

        val programArguments = buildList {
            add(javaExecutable)
            addAll(config.jvmArgs)
            add("-jar")
            add(config.jarFile.absolutePath)
            addAll(config.appArgs)
        }

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>${config.serviceId}</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |${programArguments.joinToString("\n") { "        <string>$it</string>" }}
            |    </array>
            |    <key>StandardOutPath</key>
            |    <string>${config.logPath}</string>
            |    <key>StandardErrorPath</key>
            |    <string>${config.logPath}</string>
            |    <key>KeepAlive</key>
            |    <${if (config.keepAlive) "true" else "false"}/>
            |</dict>
            |</plist>
        """.trimMargin()
    }
}
