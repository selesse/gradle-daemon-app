package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

abstract class DaemonStatusTask : DefaultTask() {
    @TaskAction
    fun status() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = getPlatformHandler()

        val status = handler.status(project, extension, logger)

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
