# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `daemonLogs` task to print daemon logs
- Example project: file-watcher demonstrating plugin usage

### Changed
- Bumped Gradle to 9.1

## 0.2.0

### Added
- GitHub Actions workflow for automated plugin publishing on tag push

### Fixed
- Compute release directory based on OS for cross-platform compatibility
- Deprecation warnings in build configuration

## 0.1.0

### Added
- Initial release of gradle-daemon-app plugin
- Support for installing Java applications as background daemons
- Cross-platform daemon management (macOS LaunchAgent, Windows Startup, Linux systemd)
- Tasks: `installDaemon`, `startDaemon`, `stopDaemon`, `restartDaemon`, `daemonStatus`, `uninstallDaemon`
- PID tracking and status reporting
- Auto-restart/keep-alive functionality

[Unreleased]: https://github.com/selesse/gradle-daemon-app/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/selesse/gradle-daemon-app/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/selesse/gradle-daemon-app/releases/tag/v0.1.0
