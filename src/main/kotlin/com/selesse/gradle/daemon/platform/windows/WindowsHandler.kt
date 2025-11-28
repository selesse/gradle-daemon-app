package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.platform.JavaHomeProvider
import com.selesse.gradle.daemon.platform.PlatformHandler
import org.gradle.api.Project
import java.io.File

class WindowsHandler(
    private val backend: DaemonBackend? = null,
) : PlatformHandler {
    override fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
    ) {
        if (extension.windows.useStartupFolder) {
            val config = buildConfig(project, extension, jarFile, javaHome)
            val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = true)
            actualBackend.install(config)
        }
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val config = buildConfig(project, extension)
        val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)
        return actualBackend.start(config)
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val config = buildConfig(project, extension)
        val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)
        return actualBackend.stop(config)
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
    ): DaemonStatus {
        val config = buildConfig(project, extension)
        val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)
        return actualBackend.getStatus(config)
    }

    override fun uninstall(
        project: Project,
        extension: DaemonAppExtension,
    ) {
        val config = buildConfig(project, extension)
        val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)

        val status = actualBackend.getStatus(config)
        if (status.running) {
            actualBackend.stop(config)
        }

        actualBackend.cleanup(config)
    }

    private fun buildConfig(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File? = null,
        javaHome: String? = null,
    ): DaemonConfig {
        val actualJarFile = jarFile ?: extension.jarTask.get().archiveFile.get().asFile
        val actualJavaHome = javaHome ?: JavaHomeProvider.get(extension)

        val actualBackend = backend ?: WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)
        val configPath = actualBackend.getDefaultConfigPath(extension.serviceId.get(), extension.windows)
        val logPath = actualBackend.getDefaultLogPath(project, extension)

        return DaemonConfig(
            serviceId = extension.serviceId.get(),
            jarFile = actualJarFile,
            javaHome = actualJavaHome,
            configPath = configPath,
            logPath = logPath,
            jvmArgs = extension.jvmArgs.getOrElse(emptyList()),
            appArgs = extension.appArgs.getOrElse(emptyList()),
            keepAlive = extension.keepAlive.getOrElse(true),
        )
    }
}
