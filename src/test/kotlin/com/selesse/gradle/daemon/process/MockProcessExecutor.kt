package com.selesse.gradle.daemon.process

/**
 * Mock implementation of ProcessExecutor for testing.
 * Allows setting up expected command responses and verifying command execution.
 *
 * Usage example:
 * ```
 * val mock = MockProcessExecutor()
 *     .mockSuccess(listOf("launchctl", "load"), stdout = "")
 *     .mockSuccess(listOf("launchctl", "list"), stdout = "12345\t0\tcom.example.daemon")
 *
 * val handler = MacOSPlistHandler(processExecutor = mock)
 * handler.start(config)
 *
 * assertTrue(mock.wasExecuted(listOf("launchctl", "load")))
 * ```
 */
class MockProcessExecutor : ProcessExecutor {
    private val responses = mutableMapOf<CommandMatcher, ProcessExecutor.CommandResult>()
    private val executedCommands = mutableListOf<List<String>>()

    /**
     * Set up a response for a specific command pattern.
     * Commands are matched by checking if they start with the given prefix.
     */
    fun mockCommand(
        commandPrefix: List<String>,
        exitCode: Int = 0,
        stdout: String = "",
        stderr: String = "",
    ): MockProcessExecutor {
        responses[CommandMatcher(commandPrefix)] =
            ProcessExecutor.CommandResult(exitCode, stdout, stderr)
        return this
    }

    /**
     * Convenience method for mocking successful commands.
     */
    fun mockSuccess(commandPrefix: List<String>, stdout: String = ""): MockProcessExecutor {
        return mockCommand(commandPrefix, exitCode = 0, stdout = stdout)
    }

    /**
     * Convenience method for mocking failed commands.
     */
    fun mockFailure(commandPrefix: List<String>, stderr: String, exitCode: Int = 1): MockProcessExecutor {
        return mockCommand(commandPrefix, exitCode = exitCode, stderr = stderr)
    }

    override fun execute(command: List<String>): ProcessExecutor.CommandResult {
        executedCommands.add(command)

        val matcher = responses.keys.firstOrNull { it.matches(command) }
        return matcher?.let { responses[it] }
            ?: ProcessExecutor.CommandResult(0, "", "")
    }

    /**
     * Verify that a command was executed.
     */
    fun wasExecuted(commandPrefix: List<String>): Boolean {
        return executedCommands.any { command ->
            command.take(commandPrefix.size) == commandPrefix
        }
    }

    /**
     * Get all executed commands for assertions.
     */
    fun getExecutedCommands(): List<List<String>> = executedCommands.toList()

    /**
     * Reset the mock state.
     */
    fun reset() {
        responses.clear()
        executedCommands.clear()
    }

    private data class CommandMatcher(val prefix: List<String>) {
        fun matches(command: List<String>): Boolean {
            return command.take(prefix.size) == prefix
        }
    }
}
