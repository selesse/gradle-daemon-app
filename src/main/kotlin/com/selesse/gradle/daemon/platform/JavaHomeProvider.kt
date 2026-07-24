package com.selesse.gradle.daemon.platform

import org.gradle.internal.os.OperatingSystem
import java.io.File

object JavaHomeProvider {
    /**
     * Resolves JAVA_HOME without a configured `javaLauncher` (which is checked first by whoever
     * wires up a task's `javaHome` property). Pure/stateless so it's safe to invoke lazily at
     * task execution time under the configuration cache.
     */
    fun resolveWithoutLauncher(): String {
        // First, check JAVA_HOME environment variable
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            return javaHome
        }

        // Second, try to find java executable in PATH
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
