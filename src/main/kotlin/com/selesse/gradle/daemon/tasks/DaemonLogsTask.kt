package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class DaemonLogsTask : AbstractDaemonTask() {
    @TaskAction
    fun logs() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        val status = handler.status(request)

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
}
