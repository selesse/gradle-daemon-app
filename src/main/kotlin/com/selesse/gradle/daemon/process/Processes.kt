package com.selesse.gradle.daemon.process

class Processes : ProcessExecutor {
    override fun execute(command: List<String>): ProcessExecutor.CommandResult {
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return ProcessExecutor.CommandResult(exitCode, stdout, stderr)
    }
}
