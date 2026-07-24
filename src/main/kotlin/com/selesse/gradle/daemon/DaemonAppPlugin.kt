package com.selesse.gradle.daemon

import com.selesse.gradle.daemon.platform.DaemonRequest
import com.selesse.gradle.daemon.platform.JavaHomeProvider
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
        extension.trackVersion.convention(true)
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
                    val javaExtension = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                    if (javaExtension != null) {
                        val launcher = javaToolchainService.launcherFor(javaExtension.toolchain)
                        extension.javaLauncher.convention(launcher)
                    }
                } catch (_: Exception) {
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

        val daemonInstall = project.tasks.register("daemonInstall", InstallDaemonTask::class.java) {
            group = "daemon"
            description = "Install and restart the daemon application"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonStart", StartDaemonTask::class.java) {
            group = "daemon"
            description = "Start the daemon application"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonStop", StopDaemonTask::class.java) {
            group = "daemon"
            description = "Stop the daemon application"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonRestart", RestartDaemonTask::class.java) {
            group = "daemon"
            description = "Restart the daemon application"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonStatus", DaemonStatusTask::class.java) {
            group = "daemon"
            description = "Show the status of the daemon application"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonLogs", DaemonLogsTask::class.java) {
            group = "daemon"
            description = "Print the daemon application logs"
            wireFrom(project, extension)
        }

        project.tasks.register("daemonUninstall", UninstallDaemonTask::class.java) {
            group = "daemon"
            description = "Uninstall the daemon application"
            wireFrom(project, extension)
        }

        project.afterEvaluate {
            val jarTask = extension.jarTask.get()
            daemonInstall.configure { dependsOn(jarTask) }
        }
    }

    /**
     * Wires this task's [AbstractDaemonTask] properties from the extension's providers, so
     * `@TaskAction` implementations never need to reach back into `Project`/`DaemonAppExtension`
     * (which is required for configuration-cache compatibility). `Property`-backed extension
     * fields are wired as live provider bindings so late mutations (e.g. in the consuming
     * project's own `afterEvaluate`) are still picked up; the plain (non-`Property`) macOS/linux
     * /windows sub-config fields are read eagerly here, which is safe because task realization
     * happens after the whole build script — including any `daemonApp { }` block — has run.
     */
    private fun AbstractDaemonTask.wireFrom(project: Project, extension: DaemonAppExtension) {
        serviceId.set(extension.serviceId)
        jvmArgs.set(extension.jvmArgs)
        appArgs.set(extension.appArgs)
        keepAlive.set(extension.keepAlive)
        trackVersion.set(extension.trackVersion)
        releaseDir.set(extension.releaseDir)
        logFile.set(extension.logFile)
        projectDir.set(project.layout.projectDirectory.asFile)
        jarFile.set(extension.jarTask.flatMap { it.archiveFile })
        javaHome.set(
            extension.javaLauncher.map { it.metadata.installationPath.asFile.absolutePath }
                .orElse(project.provider { JavaHomeProvider.resolveWithoutLauncher() }),
        )

        macOSPlistPath.set(extension.macOS.plistPath)
        linuxUserService.set(extension.linux.userService)
        linuxServicePath.set(extension.linux.servicePath)

        val windowsBackendValue = extension.windows.backend
        windowsBackend.set(
            when (windowsBackendValue) {
                is DaemonAppExtension.WindowsConfig.Backend.StartupFolder -> DaemonRequest.WINDOWS_BACKEND_STARTUP_FOLDER
                is DaemonAppExtension.WindowsConfig.Backend.WinSW -> DaemonRequest.WINDOWS_BACKEND_WINSW
            },
        )
        val winswConfig = (windowsBackendValue as? DaemonAppExtension.WindowsConfig.Backend.WinSW)?.config
        windowsWinswExecutable.set(winswConfig?.executable)
        windowsServiceDisplayName.set(winswConfig?.serviceDisplayName)
        windowsServiceDescription.set(winswConfig?.serviceDescription)
    }
}
