package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

class WindowsHandler : PlatformHandler {
    override fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
        logger: Logger,
    ) {
        if (extension.windows.useStartupFolder) {
            val startupFolder = File(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            val destinationFile = File(startupFolder, jarFile.name)

            startupFolder.mkdirs()
            jarFile.copyTo(destinationFile, overwrite = true)

            logger.lifecycle("Installed JAR to Windows startup folder: ${destinationFile.absolutePath}")
        } else {
            logger.lifecycle("Windows startup folder disabled, daemon will only run when started manually")
        }
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val jarFile = extension.jarTask.get().archiveFile.get().asFile
        val javaHome = getJavaHome(extension)
        val javawExe = File(javaHome, "bin\\javaw.exe")

        val command = buildList {
            add(javawExe.absolutePath)
            addAll(extension.jvmArgs.getOrElse(emptyList()))
            add("-jar")
            add(jarFile.absolutePath)
            addAll(extension.appArgs.getOrElse(emptyList()))
        }

        val process = ProcessBuilder(command)
            .start()

        val pid = process.pid()
        logger.lifecycle("Started daemon with PID: $pid")
        return pid
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long? {
        val jarFile = extension.jarTask.get().archiveFile.get().asFile
        val pid = findDaemonPid(jarFile)

        if (pid != null) {
            val result = executeCommand(listOf("taskkill", "/PID", pid.toString(), "/F"))
            if (result.exitCode == 0) {
                logger.lifecycle("Killed daemon with PID: $pid")
                return pid
            } else {
                logger.warn("Failed to kill daemon PID $pid: ${result.stderr}")
            }
        } else {
            logger.lifecycle("No running daemon found")
        }

        return null
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): DaemonStatus {
        val jarFile = extension.jarTask.get().archiveFile.get().asFile
        val pid = findDaemonPid(jarFile)

        return if (pid != null) {
            DaemonStatus(
                running = true,
                pid = pid,
                details = "Daemon is running",
            )
        } else {
            DaemonStatus(
                running = false,
                details = "Daemon is not running",
            )
        }
    }

    private fun findDaemonPid(jarFile: File): Long? {
        val result = executeCommand(
            listOf("wmic", "process", "where", "name='javaw.exe'", "get", "CommandLine,ProcessId"),
        )

        if (result.exitCode != 0) {
            return null
        }

        val jarName = jarFile.name
        val lines = result.stdout.lines()

        for (line in lines) {
            if (line.contains(jarName)) {
                // Extract PID from the line (format: "CommandLine... ProcessId")
                val parts = line.trim().split(Regex("\\s+"))
                val pidString = parts.lastOrNull()
                return pidString?.toLongOrNull()
            }
        }

        return null
    }

    private fun getJavaHome(extension: DaemonAppExtension): String {
        return extension.javaLauncher.orNull?.metadata?.installationPath?.asFile?.absolutePath
            ?: System.getenv("JAVA_HOME")
            ?: throw RuntimeException("Could not determine JAVA_HOME. Please configure javaLauncher in daemonApp extension.")
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
