package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

abstract class RestartDaemonTask : DefaultTask() {
    @TaskAction
    fun restart() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = getPlatformHandler()

        logger.lifecycle("Restarting daemon...")
        val (stoppedPid, startedPid) = handler.restart(project, extension, logger)

        if (stoppedPid != null) {
            logger.lifecycle("Stopped daemon with PID: $stoppedPid")
        }
        if (startedPid != null) {
            logger.lifecycle("✓ Daemon restarted with PID: $startedPid")
        } else {
            logger.lifecycle("✓ Daemon restarted (PID not available)")
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
