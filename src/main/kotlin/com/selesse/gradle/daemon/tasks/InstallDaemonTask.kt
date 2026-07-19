package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.JavaHomeProvider
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import com.selesse.gradle.daemon.process.Processes
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class InstallDaemonTask : DefaultTask() {
    @TaskAction
    fun install() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        val handler = PlatformHandlerFactory.create()

        val status = handler.status(project, extension)
        if (status.running) {
            logger.lifecycle("Stopping existing daemon (PID: ${status.pid ?: "unknown"})...")
            handler.stop(project, extension)
        }

        val jarTask = extension.jarTask.get()
        val jarFile = jarTask.archiveFile.get().asFile

        val releaseDir = DaemonVersion.resolveReleaseDir(project, extension)

        releaseDir.mkdirs()
        val releasedJar = File(releaseDir, jarFile.name)
        jarFile.copyTo(releasedJar, overwrite = true)
        logger.lifecycle("Copied JAR to: ${releasedJar.absolutePath}")

        if (extension.trackVersion.getOrElse(false)) {
            val version = DaemonVersion.captureGitSha(project, Processes())
            if (version != null) {
                DaemonVersion.write(releaseDir, version)
                logger.lifecycle("Recorded version: $version")
            } else {
                logger.lifecycle("Could not determine git SHA; skipping version tracking")
            }
        }

        val javaHome = JavaHomeProvider.get(extension)
        logger.lifecycle("Using Java home: $javaHome")

        handler.install(project, extension, releasedJar, javaHome)

        logger.lifecycle("Starting daemon...")
        val pid = handler.start(project, extension)

        if (pid != null) {
            logger.lifecycle("Started new daemon with PID: $pid")
        } else {
            logger.lifecycle("Daemon started (PID not available)")
        }

        logger.lifecycle("✓ Daemon installation complete")
    }
}
