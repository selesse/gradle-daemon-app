# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [0.6.0]

### Added

- `trackVersion` option (enabled by default): records the project's git SHA at install time and displays it in `daemonStatus`. Set `trackVersion = false` to disable.

## [0.5.0]

### Changed

- Renamed tasks to use a consistent `daemon` prefix: `daemonInstall`, `daemonStart`, `daemonStop`, `daemonRestart`, `daemonUninstall` (previously `installDaemon`, `startDaemon`, etc.)

## [0.4.1]

### Fixed

- Windows service now correctly resolves user-specific paths (APPDATA, LOCALAPPDATA, USERPROFILE) when running as LocalSystem. The installing user's environment variables are embedded in the WinSW XML at install time.

## [0.4.0]

### Added

- Windows service backend using WinSW, replacing the Startup folder approach. WinSW is bundled in the plugin JAR — no network access required at install time. The daemon runs as a proper Windows service with auto-restart and boot persistence.

### Changed

- `windows { }` DSL now uses a builder style: `winsw { }` (default) or `startupFolder()`, matching the style of `macOS { }` and `linux { }`

## [0.3.2]

### Fixed

- `installDaemon` now properly stops and restarts running daemons before updating the JAR. Previously, `launchctl load` (macOS) and `systemctl start` (Linux) would silently return success without restarting, leaving the old JAR running.

## [0.3.1]

### Fixed

- Java toolchain auto-detection now works correctly

## [0.3.0]

### Added

- `daemonLogs` task to print daemon logs
- Example project: file-watcher demonstrating plugin usage
- Automated versioning using Axion Release Plugin (derives version from git tags)
- Changelog management using JetBrains Changelog Plugin
- CI builds example projects automatically (Ubuntu only)
- Comprehensive release workflow documentation in README

### Changed

- Bumped Gradle to 9.1
- Version now auto-increments minor by default (0.2.0 → 0.3.0-SNAPSHOT)
- GitHub releases now use CHANGELOG.md content instead of auto-generated notes

## [0.2.0]

### Added

- GitHub Actions workflow for automated plugin publishing on tag push

### Fixed

- Compute release directory based on OS for cross-platform compatibility
- Deprecation warnings in build configuration

## [0.1.0]

### Added

- Initial release of gradle-daemon-app plugin
- Support for installing Java applications as background daemons
- Cross-platform daemon management (macOS LaunchAgent, Windows Startup, Linux systemd)
- Tasks: `installDaemon`, `startDaemon`, `stopDaemon`, `restartDaemon`, `daemonStatus`, `uninstallDaemon`
- PID tracking and status reporting
- Auto-restart/keep-alive functionality

[Unreleased]: https://github.com/selesse/gradle-daemon-app/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/selesse/gradle-daemon-app/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/selesse/gradle-daemon-app/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/selesse/gradle-daemon-app/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/selesse/gradle-daemon-app/commits/v0.1.0
[Unreleased]: https://github.com/selesse/gradle-daemon-app/compare/v0.5.0...HEAD
