package com.selesse.gradle.daemon.platform

import java.io.File

/**
 * Config-cache-safe snapshot of everything a [PlatformHandler] needs, resolved from
 * `DaemonAppExtension`'s providers at task configuration time. Plain values only (no
 * `Project`/`Task` references) so it can be read from a task's own properties at execution time.
 */
data class DaemonRequest(
    val serviceId: String,
    val jvmArgs: List<String>,
    val appArgs: List<String>,
    val keepAlive: Boolean,
    val releaseDir: File?,
    val logFile: File?,
    val projectDir: File,
    val jarFile: File,
    val javaHome: String,
    val macOSPlistPath: String?,
    val linuxUserService: Boolean,
    val linuxServicePath: String?,
    val windowsBackend: String,
    val windowsWinswExecutable: String?,
    val windowsServiceDisplayName: String?,
    val windowsServiceDescription: String?,
) {
    companion object {
        const val WINDOWS_BACKEND_WINSW = "winsw"
        const val WINDOWS_BACKEND_STARTUP_FOLDER = "startupFolder"
    }
}
