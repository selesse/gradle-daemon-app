package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import java.io.File

abstract class UninstallDaemonTask : DefaultTask() {
    @TaskAction
    fun uninstall() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = getPlatformHandler()

        val status = handler.status(project, extension, logger)
        if (status.running) {
            logger.lifecycle("Stopping daemon...")
            handler.stop(project, extension, logger)
        }

        if (status.configPath != null) {
            val configFile = File(status.configPath)
            if (configFile.exists()) {
                configFile.delete()
                logger.lifecycle("Removed config file: ${status.configPath}")
            }
        }

        val os = OperatingSystem.current()
        if (os.isWindows && extension.windows.useStartupFolder) {
            val jarFile = extension.jarTask.get().archiveFile.get().asFile
            val startupFolder = File(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            val startupJar = File(startupFolder, jarFile.name)
            if (startupJar.exists()) {
                startupJar.delete()
                logger.lifecycle("Removed JAR from startup folder: ${startupJar.absolutePath}")
            }
        }

        logger.lifecycle("✓ Daemon uninstalled")
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
