package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project
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
    )

    /**
     * Start the daemon on this platform.
     * Returns the PID of the started process, or null if unknown.
     */
    fun start(
        project: Project,
        extension: DaemonAppExtension,
    ): Long?

    /**
     * Stop the daemon on this platform.
     * Returns the PID of the stopped process, or null if not running.
     */
    fun stop(
        project: Project,
        extension: DaemonAppExtension,
    ): Long?

    /**
     * Get the status of the daemon.
     * Returns a map with status information (running, pid, etc.)
     */
    fun status(
        project: Project,
        extension: DaemonAppExtension,
    ): DaemonStatus

    /**
     * Restart the daemon (stop + start).
     * Returns a pair of (stopped PID, started PID).
     */
    fun restart(
        project: Project,
        extension: DaemonAppExtension,
    ): Pair<Long?, Long?> {
        val stoppedPid = stop(project, extension)
        val startedPid = start(project, extension)
        return Pair(stoppedPid, startedPid)
    }

    /**
     * Uninstall the daemon.
     * Stops the daemon if running, removes configuration files, and cleans up.
     */
    fun uninstall(
        project: Project,
        extension: DaemonAppExtension,
    )
}

data class DaemonStatus(
    val running: Boolean,
    val pid: Long? = null,
    val details: String = "",
    val configPath: String? = null,
    val logPath: String? = null,
)
