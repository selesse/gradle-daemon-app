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
        val actualBackend = backend ?: createBackend(extension)
        if (extension.windows.useNSSM || extension.windows.useStartupFolder) {
            val config = buildConfig(project, extension, jarFile, javaHome, actualBackend)
            actualBackend.install(config)
        }
    }

    override fun start(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val actualBackend = backend ?: createBackend(extension)
        val config = buildConfig(project, extension, actualBackend = actualBackend)
        return actualBackend.start(config)
    }

    override fun stop(
        project: Project,
        extension: DaemonAppExtension,
    ): Long? {
        val actualBackend = backend ?: createBackend(extension)
        val config = buildConfig(project, extension, actualBackend = actualBackend)
        return actualBackend.stop(config)
    }

    override fun status(
        project: Project,
        extension: DaemonAppExtension,
    ): DaemonStatus {
        val actualBackend = backend ?: createBackend(extension)
        val config = buildConfig(project, extension, actualBackend = actualBackend)
        return actualBackend.getStatus(config)
    }

    override fun uninstall(
        project: Project,
        extension: DaemonAppExtension,
    ) {
        val actualBackend = backend ?: createBackend(extension)
        val config = buildConfig(project, extension, actualBackend = actualBackend)

        val status = actualBackend.getStatus(config)
        if (status.running) {
            actualBackend.stop(config)
        }

        actualBackend.cleanup(config)
    }

    /**
     * Create the appropriate backend based on configuration.
     * NSSM takes precedence over startup folder if both are enabled.
     */
    private fun createBackend(extension: DaemonAppExtension): DaemonBackend {
        return if (extension.windows.useNSSM) {
            WindowsNSSMHandler(nssmPathOverride = extension.windows.nssmPath)
        } else {
            WindowsStartupHandler(useStartupFolder = extension.windows.useStartupFolder)
        }
    }

    private fun buildConfig(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File? = null,
        javaHome: String? = null,
        actualBackend: DaemonBackend? = null,
    ): DaemonConfig {
        val actualJarFile = jarFile ?: extension.jarTask.get().archiveFile.get().asFile
        val actualJavaHome = javaHome ?: JavaHomeProvider.get(extension)

        val resolvedBackend = actualBackend ?: backend ?: createBackend(extension)
        val configPath = resolvedBackend.getDefaultConfigPath(extension.serviceId.get(), extension.windows)
        val logPath = resolvedBackend.getDefaultLogPath(project, extension)

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
