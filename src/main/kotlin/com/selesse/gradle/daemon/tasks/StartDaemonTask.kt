package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction

abstract class StartDaemonTask : AbstractDaemonTask() {
    @TaskAction
    fun start() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Starting daemon...")
        val pid = handler.start(request)

        if (pid != null) {
            logger.lifecycle("✓ Daemon started with PID: $pid")
        } else {
            logger.lifecycle("✓ Daemon started (PID not available)")
        }
    }
}
