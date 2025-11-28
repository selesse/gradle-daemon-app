package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class DaemonStatusTask : DefaultTask() {
    @TaskAction
    fun status() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        val status = handler.status(project, extension)

        logger.lifecycle("Daemon Status:")
        logger.lifecycle("  Service ID: ${extension.serviceId.get()}")
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
