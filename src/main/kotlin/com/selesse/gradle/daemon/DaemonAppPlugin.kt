package com.selesse.gradle.daemon

import com.selesse.gradle.daemon.tasks.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaToolchainService
import javax.inject.Inject

abstract class DaemonAppPlugin : Plugin<Project> {
    @get:Inject
    abstract val javaToolchainService: JavaToolchainService

    override fun apply(project: Project) {
        val extension = project.extensions.create("daemonApp", DaemonAppExtension::class.java)

        extension.keepAlive.convention(true)
        extension.releaseDir.convention(
            project.layout.file(
                extension.serviceId.map { serviceId ->
                    val configBaseDir = when {
                        System.getProperty("os.name").lowercase().contains("win") -> {
                            // Windows: %APPDATA%
                            System.getenv("APPDATA")
                        }
                        System.getProperty("os.name").lowercase().contains("mac") -> {
                            // macOS: ~/Library/Application Support
                            "${System.getProperty("user.home")}/Library/Application Support"
                        }
                        else -> {
                            // Linux/Unix: XDG_DATA_HOME or ~/.local/share
                            System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share"
                        }
                    }
                    project.file("$configBaseDir/$serviceId")
                },
            ),
        )
        extension.jvmArgs.convention(emptyList())
        extension.appArgs.convention(emptyList())

        project.afterEvaluate {
            if (!extension.jarTask.isPresent) {
                val shadowJarTask = project.tasks.findByName("shadowJar")
                if (shadowJarTask != null) {
                    extension.jarTask.convention(project.provider { shadowJarTask as org.gradle.api.tasks.bundling.Jar })
                }
            }

            if (!extension.javaLauncher.isPresent) {
                try {
                    val toolchain = project.extensions.findByType(org.gradle.jvm.toolchain.JavaToolchainSpec::class.java)
                    if (toolchain != null) {
                        val launcher = javaToolchainService.launcherFor(toolchain)
                        extension.javaLauncher.convention(launcher)
                    }
                } catch (e: Exception) {
                    // Toolchain not configured, will fall back to JAVA_HOME
                }
            }

            if (!extension.serviceId.isPresent) {
                throw IllegalStateException("daemonApp.serviceId must be configured")
            }
            if (!extension.jarTask.isPresent) {
                throw IllegalStateException("daemonApp.jarTask must be configured (or shadowJar task must exist)")
            }
        }

        val installDaemon = project.tasks.register("installDaemon", InstallDaemonTask::class.java) {
            group = "daemon"
            description = "Install and restart the daemon application"
        }

        project.tasks.register("startDaemon", StartDaemonTask::class.java) {
            group = "daemon"
            description = "Start the daemon application"
        }

        project.tasks.register("stopDaemon", StopDaemonTask::class.java) {
            group = "daemon"
            description = "Stop the daemon application"
        }

        project.tasks.register("restartDaemon", RestartDaemonTask::class.java) {
            group = "daemon"
            description = "Restart the daemon application"
        }

        project.tasks.register("daemonStatus", DaemonStatusTask::class.java) {
            group = "daemon"
            description = "Show the status of the daemon application"
        }

        project.tasks.register("uninstallDaemon", UninstallDaemonTask::class.java) {
            group = "daemon"
            description = "Uninstall the daemon application"
        }

        project.afterEvaluate {
            val jarTask = extension.jarTask.get()
            installDaemon.configure { dependsOn(jarTask) }
        }
    }
}
