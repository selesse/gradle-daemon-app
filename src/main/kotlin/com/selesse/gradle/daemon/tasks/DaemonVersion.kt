package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.process.ProcessExecutor
import org.gradle.api.Project
import java.io.File

/**
 * Resolves the daemon's version (the git SHA captured at install time) so that
 * `daemonInstall` can record it and `daemonStatus` can display it.
 */
internal object DaemonVersion {
    private const val FILE_NAME = "VERSION"

    fun resolveReleaseDir(project: Project, extension: DaemonAppExtension): File {
        return extension.releaseDir.orNull?.asFile
            ?: project.layout.projectDirectory.file("release").asFile
    }

    /**
     * Returns the current git SHA of the project, with a "-dirty" suffix if the working
     * tree has uncommitted changes. Returns null if the project isn't in a git repository
     * or git isn't available.
     */
    fun captureGitSha(project: Project, processExecutor: ProcessExecutor): String? {
        val projectDir = project.projectDir.absolutePath

        val shaResult = processExecutor.execute(listOf("git", "-C", projectDir, "rev-parse", "HEAD"))
        val sha = shaResult.stdout.trim()
        if (shaResult.exitCode != 0 || sha.isEmpty()) {
            return null
        }

        val statusResult = processExecutor.execute(listOf("git", "-C", projectDir, "status", "--porcelain"))
        val isDirty = statusResult.exitCode == 0 && statusResult.stdout.isNotBlank()

        return if (isDirty) "$sha-dirty" else sha
    }

    fun write(releaseDir: File, version: String) {
        File(releaseDir, FILE_NAME).writeText(version)
    }

    fun read(releaseDir: File): String? {
        val file = File(releaseDir, FILE_NAME)
        if (!file.exists()) {
            return null
        }
        return file.readText().trim().ifEmpty { null }
    }
}
