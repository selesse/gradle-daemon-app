package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.api.Project

interface DaemonBackend {
    /**
     * Get the default config file path for this backend.
     */
    fun getDefaultConfigPath(serviceId: String, platformConfig: Any?): String

    /**
     * Get the default log file path for this backend.
     */
    fun getDefaultLogPath(project: Project, extension: DaemonAppExtension): String

    /**
     * Install/write the daemon configuration file for this backend.
     */
    fun install(config: DaemonConfig)

    /**
     * Start the daemon using this backend's tool.
     * Returns the PID of the started process, or null if unknown.
     */
    fun start(config: DaemonConfig): Long?

    /**
     * Stop the daemon using this backend's tool.
     * Returns the PID of the stopped process, or null if not running.
     */
    fun stop(config: DaemonConfig): Long?

    /**
     * Get the status of the daemon.
     */
    fun getStatus(config: DaemonConfig): DaemonStatus

    /**
     * Clean up any backend-specific files or configurations.
     */
    fun cleanup(config: DaemonConfig)
}
