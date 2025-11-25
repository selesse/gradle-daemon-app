# gradle-daemon-app

A Gradle plugin for installing and managing Java applications as persistent background daemons across macOS, Windows, and Linux.

## Features

- 🚀 **Cross-platform daemon management** - Works on macOS (LaunchAgent), Windows (Startup), and Linux (systemd)
- 🔄 **Complete lifecycle management** - Install, start, stop, restart, uninstall, and status commands
- 🎯 **Smart defaults** - Auto-detects shadowJar, uses Gradle toolchain for Java home
- 📝 **Detailed logging** - Shows PIDs, config paths, and log file locations
- ⚙️ **Flexible configuration** - Customize JVM args, log locations, service IDs, and more

## Quick Start

### 1. Apply the plugin

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.selesse.daemon-app") version "0.1.0"
}
```

### 2. Configure the daemon

```kotlin
daemonApp {
    serviceId = "com.example.my-daemon"
    jarTask = tasks.shadowJar
    jvmArgs = listOf("-Xmx512m", "-Xms256m")
}
```

### 3. Install and run

```bash
./gradlew installDaemon
```

That's it! Your application is now running as a background daemon.

## Examples

Check out the [examples/](examples/) directory for complete working examples:

- **[file-watcher](examples/file-watcher/)** - A file watcher daemon that monitors a directory for changes and prints events.

## Configuration

### Required Properties

```kotlin
daemonApp {
    // Unique service identifier (used for LaunchAgent label, systemd service name, etc.)
    serviceId = "com.example.my-daemon"

    // JAR task to use (typically shadowJar for fat JARs)
    jarTask = tasks.shadowJar
}
```

### Optional Properties

```kotlin
daemonApp {
    // JVM arguments
    jvmArgs = listOf(
        "-Xmx512m",
        "--enable-native-access=ALL-UNNAMED"
    )

    // Application arguments
    appArgs = listOf("--config", "/path/to/config")

    // Where to copy JAR locally
    // Default: Platform-specific directory:
    //   - Windows: %APPDATA%/${serviceId}
    //   - macOS: ~/Library/Application Support/${serviceId}
    //   - Linux: $XDG_DATA_HOME/${serviceId} or ~/.local/share/${serviceId}
    releaseDir = file("/custom/path")

    // Log file location (default: releaseDir/daemon.log)
    logFile = file("logs/daemon.log")

    // Auto-restart on crash (default: true)
    keepAlive = true

    // Java launcher (default: uses project toolchain)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

### Platform-Specific Configuration

#### macOS

```kotlin
daemonApp {
    macOS {
        // Custom plist file path
        // Default: ~/Library/LaunchAgents/${serviceId}.plist
        plistPath = "/path/to/custom.plist"
    }
}
```

#### Windows

```kotlin
daemonApp {
    windows {
        // Use startup folder for auto-start (default: true)
        useStartupFolder = true
    }
}
```

#### Linux

```kotlin
daemonApp {
    linux {
        // Use systemd user service vs system service (default: true)
        userService = true

        // Custom service file path
        // Default: ~/.config/systemd/user/${serviceId}.service
        servicePath = "/path/to/custom.service"
    }
}
```

## Tasks

The plugin provides six tasks in the `daemon` group:

### installDaemon

Builds the JAR, installs platform-specific configuration, and starts/restarts the daemon.

```bash
./gradlew installDaemon
```

**Output:**
```
Copied JAR to: /path/to/release/app.jar
Using Java home: /path/to/java
Installed LaunchAgent plist to: ~/Library/LaunchAgents/com.example.my-daemon.plist
Restarting daemon...
Stopped daemon via launchctl unload
Started daemon via launchctl load
Started new daemon with PID: 12345
✓ Daemon installation complete
```

### startDaemon

Starts the daemon (fails if already running).

```bash
./gradlew startDaemon
```

### stopDaemon

Stops the running daemon.

```bash
./gradlew stopDaemon
```

### restartDaemon

Stops and restarts the daemon.

```bash
./gradlew restartDaemon
```

### daemonStatus

Shows the current status of the daemon.

```bash
./gradlew daemonStatus
```

**Output:**
```
Daemon Status:
  Service ID: com.example.my-daemon
  Running: Yes
  PID: 12345
  Config: /Users/user/Library/LaunchAgents/com.example.my-daemon.plist
  Logs: /path/to/release/daemon.log
  Details: Daemon is running as LaunchAgent
```

### uninstallDaemon

Stops the daemon and removes all configuration files.

```bash
./gradlew uninstallDaemon
```

## Platform Details

### macOS (LaunchAgent)

The plugin generates a LaunchAgent plist file and installs it to `~/Library/LaunchAgents/`.

**Generated plist example:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.example.my-daemon</string>
    <key>ProgramArguments</key>
    <array>
        <string>/path/to/java</string>
        <string>-Xmx512m</string>
        <string>-jar</string>
        <string>/path/to/app.jar</string>
    </array>
    <key>StandardOutPath</key>
    <string>/path/to/daemon.log</string>
    <key>StandardErrorPath</key>
    <string>/path/to/daemon.log</string>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
```

### Windows (Startup Folder)

The plugin copies the JAR to the Windows startup folder and manages the process using `javaw.exe`.

**Startup folder:** `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup`

### Linux (systemd)

The plugin generates a systemd user service file and installs it to `~/.config/systemd/user/`.

**Generated service example:**
```ini
[Unit]
Description=com.example.my-daemon Daemon
After=network.target

[Service]
Type=simple
ExecStart=/path/to/java -Xmx512m -jar /path/to/app.jar
Restart=always
RestartSec=10
StandardOutput=append:/path/to/daemon.log
StandardError=append:/path/to/daemon.log

[Install]
WantedBy=default.target
```

## Development & Releases

This project uses automated versioning and changelog management for releases.

### Version Management

Versions are automatically derived from git tags using the [Axion Release Plugin](https://github.com/allegro/axion-release-plugin):

- **On a git tag** (e.g., `v0.3.0`): Version is `0.3.0`
- **Between tags**: Version is `0.3.0-SNAPSHOT` (auto-increments minor version)

Check the current version:
```bash
./gradlew currentVersion
```

### Changelog Management

The project uses the [JetBrains Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin) to maintain `CHANGELOG.md`.

#### During Development

Update `CHANGELOG.md` under the `[Unreleased]` section as you make changes:

```markdown
## [Unreleased]

### Added
- New feature description

### Fixed
- Bug fix description

### Changed
- Breaking change description
```

Use standard [Keep a Changelog](https://keepachangelog.com/) categories: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.

#### Creating a Release

1. **Prepare the changelog** (moves `[Unreleased]` to version header):
   ```bash
   ./gradlew patchChangelog
   ```

2. **Commit the changelog**:
   ```bash
   git add CHANGELOG.md
   git commit -m "Release 0.3.0"
   ```

3. **Tag and push**:
   ```bash
   git tag v0.3.0
   git push origin main --tags
   ```

4. **CI automatically**:
   - Derives version from tag (`0.3.0`)
   - Builds and tests the plugin
   - Publishes to Gradle Plugin Portal
   - Creates GitHub release with changelog content
