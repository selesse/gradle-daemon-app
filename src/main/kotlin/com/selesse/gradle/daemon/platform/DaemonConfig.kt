package com.selesse.gradle.daemon.platform

import java.io.File

data class DaemonConfig(
    val serviceId: String,
    val jarFile: File,
    val javaHome: String,
    val configPath: String,
    val logPath: String,
    val jvmArgs: List<String>,
    val appArgs: List<String>,
    val keepAlive: Boolean,
)
