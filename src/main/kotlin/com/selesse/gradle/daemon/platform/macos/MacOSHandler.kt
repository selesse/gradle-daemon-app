package com.selesse.gradle.daemon.platform.macos

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.platform.JavaHomeProvider
import com.selesse.gradle.daemon.platform.PlatformHandler
import org.gradle.api.Project
import java.io.File

class MacOSHandler(
    private val backend: DaemonBackend = MacOSPlistHandler(),
) : PlatformHandler {
    override fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
    ) {
        val config = buildConfig(project, extension, jarFile, javaHome)
        backend.install(config)
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val config = buildConfig(project, extension)
        return backend.start(config)
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val config = buildConfig(project, extension)
        return backend.stop(config)
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
    ): DaemonStatus {
        val config = buildConfig(project, extension)
        return backend.getStatus(config)
    }

    override fun uninstall(
        project: Project,
        extension: DaemonAppExtension,
    ) {
        val config = buildConfig(project, extension)

        val status = backend.getStatus(config)
        if (status.running) {
            backend.stop(config)
        }

        backend.cleanup(config)
    }

    private fun buildConfig(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File? = null,
        javaHome: String? = null,
    ): DaemonConfig {
        val actualJarFile = jarFile ?: extension.jarTask.get().archiveFile.get().asFile
        val actualJavaHome = javaHome ?: JavaHomeProvider.get(extension)

        val configPath = backend.getDefaultConfigPath(extension.serviceId.get(), extension.macOS)
        val logPath = backend.getDefaultLogPath(project, extension)

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
