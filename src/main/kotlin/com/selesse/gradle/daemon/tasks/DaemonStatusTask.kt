package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction

abstract class DaemonStatusTask : AbstractDaemonTask() {
    @TaskAction
    fun status() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        val status = handler.status(request)

        logger.lifecycle("Daemon Status:")
        logger.lifecycle("  Service ID: ${request.serviceId}")
        if (trackVersion.getOrElse(false)) {
            val releaseDir = DaemonVersion.resolveReleaseDir(request.releaseDir, request.projectDir)
            val version = DaemonVersion.read(releaseDir)
            logger.lifecycle("  Version: ${version ?: "unknown"}")
        }
        logger.lifecycle("  Running: ${if (status.running) "Yes" else "No"}")
        if (status.pid != null) {
            logger.lifecycle("  PID: ${status.pid}")
        }
        if (status.configPath != null) {
            logger.lifecycle("  Config: ${status.configPath}")
        }
        if (status.logPath != null) {
            logger.lifecycle("  Logs: ${status.logPath}")
        }
        if (status.details.isNotEmpty()) {
            logger.lifecycle("  Details: ${status.details}")
        }
    }
}
