package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.internal.os.OperatingSystem
import java.io.File

object JavaHomeProvider {
    fun get(extension: DaemonAppExtension): String {
        // First, check if explicitly configured via javaLauncher
        val launcherHome = extension.javaLauncher.orNull?.metadata?.installationPath?.asFile?.absolutePath
        if (launcherHome != null) {
            return launcherHome
        }

        // Second, check JAVA_HOME environment variable
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            return javaHome
        }

        // Third, try to find java executable in PATH
        val javaExecutable = if (OperatingSystem.current().isWindows) "java.exe" else "java"
        val processBuilder = ProcessBuilder("which", javaExecutable)
        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (output.isNotEmpty()) {
            val javaPath = File(output).canonicalFile
            // java executable is in JAVA_HOME/bin/java, so go up two directories
            return javaPath.parentFile.parentFile.absolutePath
        }

        throw IllegalStateException(
            "Could not determine JAVA_HOME. Please set JAVA_HOME environment variable " +
                "or configure javaLauncher.",
        )
    }
}
