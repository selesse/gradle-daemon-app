package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.platform.linux.LinuxHandler
import com.selesse.gradle.daemon.platform.macos.MacOSHandler
import com.selesse.gradle.daemon.platform.windows.WindowsHandler
import org.gradle.internal.os.OperatingSystem

object PlatformHandlerFactory {
    /**
     * When this system property is set to "noop", [create] returns a [NoopPlatformHandler]
     * instead of a real platform handler. Test-only escape hatch so integration tests can
     * exercise the mutating daemon tasks without touching launchctl/systemctl/WinSW.
     */
    const val TEST_HANDLER_PROPERTY = "com.selesse.daemon.testHandler"
    private const val NOOP_TEST_HANDLER = "noop"

    fun create(): PlatformHandler {
        if (System.getProperty(TEST_HANDLER_PROPERTY) == NOOP_TEST_HANDLER) {
            return NoopPlatformHandler()
        }

        val os = OperatingSystem.current()
        return when {
            os.isMacOsX -> MacOSHandler()
            os.isWindows -> WindowsHandler()
            os.isLinux -> LinuxHandler()
            else -> throw UnsupportedOperationException("Platform ${os.name} is not supported")
        }
    }
}
