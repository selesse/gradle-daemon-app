package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

abstract class StopDaemonTask : DefaultTask() {
    @TaskAction
    fun stop() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = getPlatformHandler()

        logger.lifecycle("Stopping daemon...")
        val pid = handler.stop(project, extension, logger)

        if (pid != null) {
            logger.lifecycle("✓ Daemon stopped (PID: $pid)")
        } else {
            logger.lifecycle("✓ Daemon stopped")
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
