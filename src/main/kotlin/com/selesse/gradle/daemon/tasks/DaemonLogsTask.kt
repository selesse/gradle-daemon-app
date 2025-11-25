package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import java.io.File

abstract class DaemonLogsTask : DefaultTask() {
    @TaskAction
    fun logs() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = getPlatformHandler()

        val status = handler.status(project, extension, logger)

        if (status.logPath == null) {
            logger.lifecycle("No log file path available")
            return
        }

        val logFile = File(status.logPath)
        if (!logFile.exists()) {
            logger.lifecycle("Log file does not exist: ${status.logPath}")
            return
        }

        logger.lifecycle("Daemon logs (${status.logPath}):")
        logger.lifecycle("")

        try {
            logFile.forEachLine { line ->
                logger.lifecycle(line)
            }
        } catch (e: Exception) {
            logger.error("Failed to read log file: ${e.message}")
        }
    }

    private fun getPlatformHandler(): PlatformHandler {
        val os = OperatingSystem.current()
        return when {
            os.isMacOsX -> MacOSHandler()
            os.isWindows -> WindowsHandler()
            os.isLinux -> LinuxHandler()
            else -> throw UnsupportedOperationException("Platform ${os.name} is not supported")
        }
    }
}
