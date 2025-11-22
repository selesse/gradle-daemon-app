package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.DaemonAppExtension
import com.selesse.gradle.daemon.platform.LinuxHandler
import com.selesse.gradle.daemon.platform.MacOSHandler
import com.selesse.gradle.daemon.platform.PlatformHandler
import com.selesse.gradle.daemon.platform.WindowsHandler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
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

        val javaHome = getJavaHome(extension)
        logger.lifecycle("Using Java home: $javaHome")

        val handler = getPlatformHandler()
        handler.install(project, extension, releasedJar, javaHome, logger)

        logger.lifecycle("Restarting daemon...")
        val (stoppedPid, startedPid) = handler.restart(project, extension, logger)

        if (stoppedPid != null) {
            logger.lifecycle("Killed existing daemon with PID: $stoppedPid")
        }
        if (startedPid != null) {
            logger.lifecycle("Started new daemon with PID: $startedPid")
        } else {
            logger.lifecycle("Daemon started (PID not available)")
        }

        logger.lifecycle("✓ Daemon installation complete")
    }

    private fun getJavaHome(extension: DaemonAppExtension): String {
        val launcherHome = extension.javaLauncher.orNull?.metadata?.installationPath?.asFile?.absolutePath
        if (launcherHome != null) {
            return launcherHome
        }

        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            return javaHome
        }

        val javaExecutable = if (OperatingSystem.current().isWindows) "java.exe" else "java"
        val processBuilder = ProcessBuilder("which", javaExecutable)
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (output.isNotEmpty()) {
            val javaPath = File(output).canonicalFile
            return javaPath.parentFile.parentFile.absolutePath
        }

        throw IllegalStateException(
            "Could not determine JAVA_HOME. Please set JAVA_HOME environment variable " +
                "or configure javaLauncher in daemonApp extension.",
        )
    }

    private fun getPlatformHandler(): PlatformHandler {
        val os = OperatingSystem.current()
        return when {
            os.isMacOsX -> MacOSHandler()
            os.isWindows -> WindowsHandler()
            os.isLinux -> LinuxHandler()
            else -> throw UnsupportedOperationException("Platform ${os.name} is not supported")
        }
    }
}
