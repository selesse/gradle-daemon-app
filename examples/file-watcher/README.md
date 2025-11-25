# File Watcher Example

This example demonstrates how to use the `daemon-app` Gradle plugin to create a background daemon that monitors a directory for file changes.

## What it does

The file watcher daemon:
- Monitors a directory recursively for file changes (defaults to this repository's root)
- Accepts an optional path argument to watch a different directory
- Prints timestamps and event types (CREATE, MODIFY, DELETE) for each change
- Runs as a background daemon using the platform's native daemon system (launchd on macOS, systemd on Linux)
- Uses [io.methvin:directory-watcher](https://github.com/gmethvin/directory-watcher) for cross-platform file watching

## Building and Running

### Install and Start the Daemon

```bash
./gradlew installDaemon
```

## Testing the File Watcher

Once the daemon is running, try creating, modifying, or deleting files in the current directory:

```bash
# Create a file
echo "test" > test.txt

# Modify the file
echo "modified" >> test.txt

# Delete the file
rm test.txt
```
