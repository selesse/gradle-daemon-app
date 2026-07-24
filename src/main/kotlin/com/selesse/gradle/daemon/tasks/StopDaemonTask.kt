package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction

abstract class StopDaemonTask : AbstractDaemonTask() {
    @TaskAction
    fun stop() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Stopping daemon...")
        val pid = handler.stop(request)

        if (pid != null) {
            logger.lifecycle("✓ Daemon stopped (PID: $pid)")
        } else {
            logger.lifecycle("✓ Daemon stopped")
        }
    }
}
