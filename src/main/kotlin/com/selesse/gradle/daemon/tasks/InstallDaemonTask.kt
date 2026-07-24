package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import com.selesse.gradle.daemon.process.Processes
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class InstallDaemonTask : AbstractDaemonTask() {
    @TaskAction
    fun install() {
        val request = daemonRequest()
        val handler = PlatformHandlerFactory.create()

        val status = handler.status(request)
        if (status.running) {
            logger.lifecycle("Stopping existing daemon (PID: ${status.pid ?: "unknown"})...")
            handler.stop(request)
        }

        val releaseDir = DaemonVersion.resolveReleaseDir(request.releaseDir, request.projectDir)

        releaseDir.mkdirs()
        val releasedJar = File(releaseDir, request.jarFile.name)
        request.jarFile.copyTo(releasedJar, overwrite = true)
        logger.lifecycle("Copied JAR to: ${releasedJar.absolutePath}")

        if (trackVersion.getOrElse(false)) {
            val version = DaemonVersion.captureGitSha(request.projectDir, Processes())
            if (version != null) {
                DaemonVersion.write(releaseDir, version)
                logger.lifecycle("Recorded version: $version")
            } else {
                logger.lifecycle("Could not determine git SHA; skipping version tracking")
            }
        }

        logger.lifecycle("Using Java home: ${request.javaHome}")

        handler.install(request, releasedJar)

        logger.lifecycle("Starting daemon...")
        val pid = handler.start(request)

        if (pid != null) {
            logger.lifecycle("Started new daemon with PID: $pid")
        } else {
            logger.lifecycle("Daemon started (PID not available)")
        }

        logger.lifecycle("✓ Daemon installation complete")
    }
}
