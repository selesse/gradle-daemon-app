package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class RestartDaemonTask : DefaultTask() {
    @TaskAction
    fun restart() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Restarting daemon...")
        val (stoppedPid, startedPid) = handler.restart(project, extension)

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
