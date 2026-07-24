package com.selesse.gradle.daemon.platform.windows

import com.selesse.gradle.daemon.platform.DaemonBackend
import com.selesse.gradle.daemon.platform.DaemonConfig
import com.selesse.gradle.daemon.platform.DaemonRequest
import com.selesse.gradle.daemon.platform.DaemonStatus
import com.selesse.gradle.daemon.platform.PlatformHandler
import java.io.File

class WindowsHandler(
    private val backend: DaemonBackend? = null,
) : PlatformHandler {
    override fun install(request: DaemonRequest, releasedJarFile: File) {
        val config = buildConfig(request, releasedJarFile)
        selectBackend(request).install(config)
    }

    override fun start(request: DaemonRequest): Long? {
        val config = buildConfig(request)
        return selectBackend(request).start(config)
    }

    override fun stop(request: DaemonRequest): Long? {
        val config = buildConfig(request)
        return selectBackend(request).stop(config)
    }

    override fun status(request: DaemonRequest): DaemonStatus {
        val config = buildConfig(request)
        return selectBackend(request).getStatus(config)
    }

    override fun uninstall(request: DaemonRequest) {
        val config = buildConfig(request)
        val actualBackend = selectBackend(request)

        val status = actualBackend.getStatus(config)
        if (status.running) {
            actualBackend.stop(config)
        }

        actualBackend.cleanup(config)
    }

    private fun selectBackend(request: DaemonRequest): DaemonBackend {
        return backend ?: when (request.windowsBackend) {
            DaemonRequest.WINDOWS_BACKEND_STARTUP_FOLDER -> WindowsStartupHandler()
            else -> WindowsWinswHandler(
                winswExecutablePath = request.windowsWinswExecutable,
                serviceDisplayName = request.windowsServiceDisplayName,
                serviceDescription = request.windowsServiceDescription,
            )
        }
    }

    private fun buildConfig(
        request: DaemonRequest,
        jarFileOverride: File? = null,
    ): DaemonConfig {
        val actualBackend = selectBackend(request)
        val configPath = actualBackend.getDefaultConfigPath(request.serviceId, null)
        val logPath = actualBackend.getDefaultLogPath(request.serviceId, request.releaseDir, request.logFile, request.projectDir)

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
