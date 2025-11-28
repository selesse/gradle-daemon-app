package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class StartDaemonTask : DefaultTask() {
    @TaskAction
    fun start() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        logger.lifecycle("Starting daemon...")
        val pid = handler.start(project, extension)

        if (pid != null) {
            logger.lifecycle("✓ Daemon started with PID: $pid")
        } else {
            logger.lifecycle("✓ Daemon started (PID not available)")
        }
    }
}
