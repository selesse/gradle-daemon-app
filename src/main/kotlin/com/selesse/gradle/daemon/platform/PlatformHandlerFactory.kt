package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.platform.linux.LinuxHandler
import com.selesse.gradle.daemon.platform.macos.MacOSHandler
import com.selesse.gradle.daemon.platform.windows.WindowsHandler
import org.gradle.internal.os.OperatingSystem

object PlatformHandlerFactory {
    fun create(): PlatformHandler {
        val os = OperatingSystem.current()
        return when {
            os.isMacOsX -> MacOSHandler()
            os.isWindows -> WindowsHandler()
            os.isLinux -> LinuxHandler()
            else -> throw UnsupportedOperationException("Platform ${os.name} is not supported")
        }
    }
}
