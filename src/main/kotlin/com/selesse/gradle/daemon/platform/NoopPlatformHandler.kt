package com.selesse.gradle.daemon.platform

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Test-only [PlatformHandler] that logs what it would do without touching real system service
 * managers (launchctl/systemctl/WinSW). Enabled via [PlatformHandlerFactory.TEST_HANDLER_PROPERTY]
 * so integration tests can exercise `daemonInstall`/`daemonStart`/`daemonStop`/`daemonRestart`
 * end to end (including under `--configuration-cache`) without side effects on the host machine.
 */
internal class NoopPlatformHandler : PlatformHandler {
    private val logger = Logging.getLogger(NoopPlatformHandler::class.java)

    override fun install(request: DaemonRequest, releasedJarFile: File) {
        logger.lifecycle("[noop] Would install daemon '{}' using jar {}", request.serviceId, releasedJarFile)
    }

    override fun start(request: DaemonRequest): Long? {
        logger.lifecycle("[noop] Would start daemon '{}'", request.serviceId)
        return null
    }

    override fun stop(request: DaemonRequest): Long? {
        logger.lifecycle("[noop] Would stop daemon '{}'", request.serviceId)
        return null
    }

    override fun status(request: DaemonRequest): DaemonStatus {
        logger.lifecycle("[noop] Would check status of daemon '{}'", request.serviceId)
        return DaemonStatus(running = false, details = "noop handler (test mode)")
    }

    override fun uninstall(request: DaemonRequest) {
        logger.lifecycle("[noop] Would uninstall daemon '{}'", request.serviceId)
    }
}
