package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class UninstallDaemonTask : DefaultTask() {
    @TaskAction
    fun uninstall() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        handler.uninstall(project, extension)

        logger.lifecycle("✓ Daemon uninstalled")
    }
}
