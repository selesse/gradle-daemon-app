package com.selesse.gradle.daemon.platform.macos

import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonRequest
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.platform.PlatformHandler
import java.io.File

class MacOSHandler(
    private val backend: DaemonBackend = MacOSPlistHandler(),
) : PlatformHandler {
    override fun install(request: DaemonRequest, releasedJarFile: File) {
        val config = buildConfig(request, releasedJarFile)
        backend.install(config)
    }

    override fun start(request: DaemonRequest): Long? {
        val config = buildConfig(request)
        return backend.start(config)
    }

    override fun stop(request: DaemonRequest): Long? {
        val config = buildConfig(request)
        return backend.stop(config)
    }

    override fun status(request: DaemonRequest): DaemonStatus {
        val config = buildConfig(request)
        return backend.getStatus(config)
    }

    override fun uninstall(request: DaemonRequest) {
        val config = buildConfig(request)

        val status = backend.getStatus(config)
        if (status.running) {
            backend.stop(config)
        }

        backend.cleanup(config)
    }

    private fun buildConfig(
        request: DaemonRequest,
        jarFileOverride: File? = null,
    ): DaemonConfig {
        val configPath = backend.getDefaultConfigPath(request.serviceId, request.macOSPlistPath)
        val logPath = backend.getDefaultLogPath(request.serviceId, request.releaseDir, request.logFile, request.projectDir)

        return DaemonConfig(
            serviceId = request.serviceId,
            jarFile = jarFileOverride ?: request.jarFile,
            javaHome = request.javaHome,
            configPath = configPath,
            logPath = logPath,
            jvmArgs = request.jvmArgs,
            appArgs = request.appArgs,
            keepAlive = request.keepAlive,
        )
    }
}
