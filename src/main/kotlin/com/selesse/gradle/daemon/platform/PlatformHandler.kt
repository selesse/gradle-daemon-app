package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Platform-specific daemon management interface.
 */
interface PlatformHandler {
    /**
     * Install daemon configuration files for this platform.
     */
    fun install(
        project: Project,
        extension: DaemonAppExtension,
        jarFile: File,
        javaHome: String,
        logger: Logger,
    )

    /**
     * Start the daemon on this platform.
     * Returns the PID of the started process, or null if unknown.
     */
    fun start(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long?

    /**
     * Stop the daemon on this platform.
     * Returns the PID of the stopped process, or null if not running.
     */
    fun stop(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Long?

    /**
     * Get the status of the daemon.
     * Returns a map with status information (running, pid, etc.)
     */
    fun status(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): DaemonStatus

    /**
     * Restart the daemon (stop + start).
     * Returns a pair of (stopped PID, started PID).
     */
    fun restart(
        project: Project,
        extension: DaemonAppExtension,
        logger: Logger,
    ): Pair<Long?, Long?> {
        val stoppedPid = stop(project, extension, logger)
        val startedPid = start(project, extension, logger)
        return Pair(stoppedPid, startedPid)
    }
}

data class DaemonStatus(
    val running: Boolean,
    val pid: Long? = null,
    val details: String = "",
    val configPath: String? = null,
    val logPath: String? = null,
)
