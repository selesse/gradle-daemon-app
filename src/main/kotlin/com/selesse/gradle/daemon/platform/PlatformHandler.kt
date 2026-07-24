package com.selesse.gradle.daemon.platform

import java.io.File

/**
 * Platform-specific daemon management interface.
 */
interface PlatformHandler {
    /**
     * Install daemon configuration files for this platform.
     * [releasedJarFile] is the copy of the built JAR that `daemonInstall` places in the release
     * directory (distinct from [DaemonRequest.jarFile], the original build output).
     */
    fun install(request: DaemonRequest, releasedJarFile: File)

    /**
     * Start the daemon on this platform.
     * Returns the PID of the started process, or null if unknown.
     */
    fun start(request: DaemonRequest): Long?

    /**
     * Stop the daemon on this platform.
     * Returns the PID of the stopped process, or null if not running.
     */
    fun stop(request: DaemonRequest): Long?

    /**
     * Get the status of the daemon.
     * Returns a map with status information (running, pid, etc.)
     */
    fun status(request: DaemonRequest): DaemonStatus

    /**
     * Restart the daemon (stop + start).
     * Returns a pair of (stopped PID, started PID).
     */
    fun restart(request: DaemonRequest): Pair<Long?, Long?> {
        val stoppedPid = stop(request)
        val startedPid = start(request)
        return Pair(stoppedPid, startedPid)
    }

    /**
     * Uninstall the daemon.
     * Stops the daemon if running, removes configuration files, and cleans up.
     */
    fun uninstall(request: DaemonRequest)
}

data class DaemonStatus(
    val running: Boolean,
    val pid: Long? = null,
    val details: String = "",
    val configPath: String? = null,
    val logPath: String? = null,
)
