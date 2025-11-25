package com.selesse.filewatcher

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("FileWatcher")

fun main(args: Array<String>) {
    val pathArg = args.firstOrNull() ?: "."
    val watchPath = Paths.get(pathArg).toAbsolutePath().normalize()

    if (!Files.exists(watchPath)) {
        logger.error("Path does not exist: {}", watchPath)
        exitProcess(1)
    }

    if (!watchPath.isDirectory()) {
        logger.error("Path is not a directory: {}", watchPath)
        exitProcess(1)
    }

    logger.info("File Watcher Daemon Started")
    logger.info("Watching directory: {}", watchPath)
    logger.info("Press Ctrl+C to stop")

    val watcher = DirectoryWatcher.builder()
        .path(watchPath)
        .listener { event ->
            handleFileEvent(event)
        }
        .build()

    watcher.watch()
}

private fun handleFileEvent(event: DirectoryChangeEvent) {
    val eventType = event.eventType()
    val path = event.path()

    logger.info("{}: {}", eventType, path)
}
