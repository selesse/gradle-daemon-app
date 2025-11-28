package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.JavaHomeProvider
import com.selesse.gradle.daemon.platform.PlatformHandlerFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class InstallDaemonTask : DefaultTask() {
    @TaskAction
    fun install() {
        val extension = project.extensions.getByType(DaemonAppExtension::class.java)

        val jarTask = extension.jarTask.get()
        val jarFile = jarTask.archiveFile.get().asFile

        val releaseDir = extension.releaseDir.orNull?.asFile
            ?: project.layout.projectDirectory.file("release").asFile

        releaseDir.mkdirs()
        val releasedJar = File(releaseDir, jarFile.name)
        jarFile.copyTo(releasedJar, overwrite = true)
        logger.lifecycle("Copied JAR to: ${releasedJar.absolutePath}")

        val javaHome = JavaHomeProvider.get(extension)
        logger.lifecycle("Using Java home: $javaHome")

        val handler = PlatformHandlerFactory.create()
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
