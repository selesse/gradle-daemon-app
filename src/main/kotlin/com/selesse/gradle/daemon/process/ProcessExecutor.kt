package com.selesse.gradle.daemon.process

interface ProcessExecutor {
    fun execute(command: List<String>): CommandResult

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
