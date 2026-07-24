package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction

abstract class RestartDaemonTask : AbstractDaemonTask() {
    @TaskAction
    fun restart() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Restarting daemon...")
        val (stoppedPid, startedPid) = handler.restart(request)

        if (stoppedPid != null) {
            logger.lifecycle("Stopped daemon with PID: $stoppedPid")
        }
        if (startedPid != null) {
            logger.lifecycle("✓ Daemon restarted with PID: $startedPid")
        } else {
            logger.lifecycle("✓ Daemon restarted (PID not available)")
        }
    }
}
