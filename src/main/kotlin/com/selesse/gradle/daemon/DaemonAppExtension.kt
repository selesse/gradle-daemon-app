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
        /**
         * Optional: Use Windows startup folder for auto-start.
         * Default: true
         *
         * Note: This is mutually exclusive with [useNSSM]. If both are true,
         * NSSM takes precedence.
         */
        var useStartupFolder: Boolean = true

        /**
         * Optional: Use NSSM (Non-Sucking Service Manager) to run as a Windows service.
         * Default: false
         *
         * When enabled, the daemon will be installed as a proper Windows service
         * using NSSM, with support for automatic restart, service control integration,
         * and centralized logging.
         *
         * NSSM must be installed and available in PATH, or [nssmPath] must be set.
         *
         * @see <a href="https://nssm.cc">NSSM Homepage</a>
         */
        var useNSSM: Boolean = false

        /**
         * Optional: Path to the NSSM executable.
         * Default: "nssm.exe" (assumes NSSM is in PATH)
         *
         * Set this if NSSM is installed in a non-standard location.
         */
        var nssmPath: String? = null
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
