package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.tasks.TaskAction

abstract class UninstallDaemonTask : AbstractDaemonTask() {
    @TaskAction
    fun uninstall() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        handler.uninstall(request)

        logger.lifecycle("✓ Daemon uninstalled")
    }
}
