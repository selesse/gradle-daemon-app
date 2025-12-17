package com.selesse.gradle.daemon.platform.windows

import org.gradle.api.logging.Logging
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

/**
 * Provides the NSSM (Non-Sucking Service Manager) executable.
 *
 * Downloads the latest NSSM version from the official website if not already cached.
 * The executable is stored in %APPDATA%\gradle-daemon-app\nssm\{version}\.
 *
 * @see <a href="https://nssm.cc">NSSM Homepage</a>
 */
class NSSMProvider(
    private val appDataDirOverride: File? = null,
) {
    private val logger = Logging.getLogger(NSSMProvider::class.java)

    companion object {
        private const val NSSM_DOWNLOAD_PAGE = "https://nssm.cc/download"
        private const val NSSM_BASE_URL = "https://nssm.cc"
        private const val APP_DIR_NAME = "gradle-daemon-app"
        private const val FALLBACK_VERSION = "2.24"
        private const val FALLBACK_URL = "https://nssm.cc/release/nssm-2.24.zip"
    }

    private fun getAppDataDir(): File {
        if (appDataDirOverride != null) {
            return appDataDirOverride
        }
        val appData = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        return File(appData, APP_DIR_NAME)
    }

    /**
     * Returns the path to the NSSM executable, downloading it if necessary.
     */
    fun getNssmPath(): File {
        val nssmBaseDir = File(getAppDataDir(), "nssm")

        // Check if any version is already downloaded
        val existingExe = findExistingNssm(nssmBaseDir)
        if (existingExe != null) {
            return existingExe
        }

        return downloadLatest(nssmBaseDir)
    }

    private fun findExistingNssm(nssmBaseDir: File): File? {
        if (!nssmBaseDir.exists()) return null

        // Look for nssm.exe in any version subdirectory
        val versionDirs = nssmBaseDir.listFiles { file -> file.isDirectory } ?: return null
        for (versionDir in versionDirs.sortedByDescending { it.name }) {
            val nssmExe = File(versionDir, "nssm.exe")
            if (nssmExe.exists()) {
                return nssmExe
            }
        }
        return null
    }

    private fun downloadLatest(nssmBaseDir: File): File {
        val (version, downloadUrl) = fetchLatestVersion()

        logger.lifecycle("Downloading NSSM {}...", version)

        val versionDir = File(nssmBaseDir, version)
        versionDir.mkdirs()
        val nssmExe = File(versionDir, "nssm.exe")

        try {
            val url = URI(downloadUrl).toURL()
            url.openStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        // Look for the 64-bit executable: nssm-X.XX/win64/nssm.exe
                        if (entry.name.contains("win64") && entry.name.endsWith("nssm.exe")) {
                            nssmExe.outputStream().use { output ->
                                zip.copyTo(output)
                            }
                            logger.lifecycle("Downloaded NSSM {} to: {}", version, nssmExe.absolutePath)
                            return nssmExe
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            throw RuntimeException("Could not find nssm.exe in downloaded archive")
        } catch (e: Exception) {
            throw RuntimeException("Failed to download NSSM from $downloadUrl: ${e.message}", e)
        }
    }

    /**
     * Fetches the download page and parses the latest version.
     * Returns a pair of (version, downloadUrl).
     *
     * Prefers the "pre-release" build (e.g., 2.24-101-g897c7ad) as it's recommended
     * for Windows 10 Creators Update and newer systems.
     */
    private fun fetchLatestVersion(): Pair<String, String> {
        try {
            val pageContent = URI(NSSM_DOWNLOAD_PAGE).toURL().readText()

            // Try to find pre-release version first (recommended for modern Windows)
            // Pattern: /ci/nssm-2.24-101-g897c7ad.zip
            val preReleasePattern = Regex("""/ci/nssm-([^"]+)\.zip""")
            val preReleaseMatch = preReleasePattern.find(pageContent)
            if (preReleaseMatch != null) {
                val version = preReleaseMatch.groupValues[1]
                val url = "$NSSM_BASE_URL/ci/nssm-$version.zip"
                return version to url
            }

            // Fall back to stable release
            // Pattern: /release/nssm-2.24.zip
            val releasePattern = Regex("""/release/nssm-([^"]+)\.zip""")
            val releaseMatch = releasePattern.find(pageContent)
            if (releaseMatch != null) {
                val version = releaseMatch.groupValues[1]
                val url = "$NSSM_BASE_URL/release/nssm-$version.zip"
                return version to url
            }

            logger.warn("Could not parse NSSM version from download page, using fallback")
        } catch (e: Exception) {
            logger.warn("Failed to fetch NSSM download page: {}, using fallback version", e.message)
        }

        return FALLBACK_VERSION to FALLBACK_URL
    }
}
