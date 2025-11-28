package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class StopDaemonTask : DefaultTask() {
    @TaskAction
    fun stop() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Stopping daemon...")
        val pid = handler.stop(project, extension)

        if (pid != null) {
            logger.lifecycle("✓ Daemon stopped (PID: $pid)")
        } else {
            logger.lifecycle("✓ Daemon stopped")
        }
    }
}
