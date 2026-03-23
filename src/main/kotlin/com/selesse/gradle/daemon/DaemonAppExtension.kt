package com.selesse.gradle.daemon

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import javax.inject.Inject

abstract class DaemonAppExtension @Inject constructor() {
    /**
     * Required: Service identifier used for LaunchAgent label, systemd service name, etc.
     * Example: "com.selesse.steam-watcher"
     */
    @get:Input
    abstract val serviceId: Property<String>

    /**
     * Required: The JAR task to use for building the daemon application.
     * Typically, this would be the shadowJar task for a fat JAR.
     */
    @get:Input
    abstract val jarTask: Property<Jar>

    /**
     * Optional: JVM arguments to pass to the daemon.
     * Example: listOf("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
     */
    @get:Input
    @get:Optional
    abstract val jvmArgs: ListProperty<String>

    /**
     * Optional: Application arguments to pass to the JAR.
     */
    @get:Input
    @get:Optional
    abstract val appArgs: ListProperty<String>

    /**
     * Optional: Directory where the JAR will be copied locally.
     * Default: Platform-specific directory + serviceId:
     *   - Windows: %APPDATA%/${serviceId}
     *   - macOS: ~/Library/Application Support/${serviceId}
     *   - Linux: $XDG_DATA_HOME/${serviceId} or ~/.local/share/${serviceId}
     */
    @get:InputFile
    @get:Optional
    abstract val releaseDir: RegularFileProperty

    /**
     * Optional: Log file location for daemon output.
     * Default: releaseDir/daemon.log
     */
    @get:InputFile
    @get:Optional
    abstract val logFile: RegularFileProperty

    /**
     * Optional: Keep alive / auto-restart on crash.
     * Default: true
     */
    @get:Input
    @get:Optional
    abstract val keepAlive: Property<Boolean>

    /**
     * Optional: Java launcher to use for the daemon.
     * If not specified, uses the project's toolchain.
     */
    @get:Input
    @get:Optional
    abstract val javaLauncher: Property<JavaLauncher>

    /**
     * macOS-specific configuration
     */
    val macOS: MacOSConfig = MacOSConfig()

    /**
     * Windows-specific configuration
     */
    val windows: WindowsConfig = WindowsConfig()

    /**
     * Linux-specific configuration
     */
    val linux: LinuxConfig = LinuxConfig()

    fun macOS(configure: MacOSConfig.() -> Unit) {
        configure(macOS)
    }

    fun windows(configure: WindowsConfig.() -> Unit) {
        configure(windows)
    }

    fun linux(configure: LinuxConfig.() -> Unit) {
        configure(linux)
    }

    class MacOSConfig {
        /**
         * Optional: Custom plist file path.
         * Default: ~/Library/LaunchAgents/${serviceId}.plist
         */
        var plistPath: String? = null
    }

    class WindowsConfig {
        internal sealed class Backend {
            data class WinSW(val config: WinSWConfig) : Backend()
            object StartupFolder : Backend()
        }

        internal var backend: Backend = Backend.WinSW(WinSWConfig())

        /**
         * Use WinSW as the service backend (default). Creates a proper Windows service that
         * auto-starts on boot and survives reboots. Requires elevation for install/uninstall.
         */
        fun winsw(configure: WinSWConfig.() -> Unit = {}) {
            backend = Backend.WinSW(WinSWConfig().apply(configure))
        }

        /**
         * Use the Windows Startup folder as the service backend (legacy). The daemon starts
         * when the user logs in. Does not require elevation.
         */
        fun startupFolder() {
            backend = Backend.StartupFolder
        }

        class WinSWConfig {
            /** Optional: Path to a custom WinSW executable. If not set, the bundled binary is used. */
            var executable: String? = null

            /** Optional: Display name for the Windows service. Defaults to serviceId. */
            var serviceDisplayName: String? = null

            /** Optional: Description for the Windows service. Defaults to "{serviceId} Daemon Service". */
            var serviceDescription: String? = null
        }
    }

    class LinuxConfig {
        /**
         * Optional: Use systemd user service (vs system service).
         * Default: true
         */
        var userService: Boolean = true

        /**
         * Optional: Custom systemd service file path.
         * Default: ~/.config/systemd/user/${serviceId}.service
         */
        var servicePath: String? = null
    }
}
