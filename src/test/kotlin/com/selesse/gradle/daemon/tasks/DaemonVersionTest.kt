package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.process.MockProcessExecutor
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DaemonVersionTest {

    @Test
    fun `captureGitSha returns sha when working tree is clean`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val processExecutor = MockProcessExecutor()
            .mockSuccess(listOf("git", "-C", project.projectDir.absolutePath, "rev-parse", "HEAD"), stdout = "abc1234\n")
            .mockSuccess(listOf("git", "-C", project.projectDir.absolutePath, "status", "--porcelain"), stdout = "")

        val version = DaemonVersion.captureGitSha(project, processExecutor)

        assertEquals("abc1234", version)
    }

    @Test
    fun `captureGitSha appends dirty suffix when working tree has changes`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val processExecutor = MockProcessExecutor()
            .mockSuccess(listOf("git", "-C", project.projectDir.absolutePath, "rev-parse", "HEAD"), stdout = "abc1234\n")
            .mockSuccess(listOf("git", "-C", project.projectDir.absolutePath, "status", "--porcelain"), stdout = " M file.txt\n")

        val version = DaemonVersion.captureGitSha(project, processExecutor)

        assertEquals("abc1234-dirty", version)
    }

    @Test
    fun `captureGitSha returns null when not a git repository`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val processExecutor = MockProcessExecutor()
            .mockFailure(
                listOf("git", "-C", project.projectDir.absolutePath, "rev-parse", "HEAD"),
                stderr = "fatal: not a git repository",
            )

        val version = DaemonVersion.captureGitSha(project, processExecutor)

        assertNull(version)
    }

    @Test
    fun `write and read round-trip the version file`(@TempDir tempDir: Path) {
        val releaseDir = tempDir.toFile()

        DaemonVersion.write(releaseDir, "abc1234")

        assertEquals("abc1234", DaemonVersion.read(releaseDir))
    }

    @Test
    fun `read returns null when no version file exists`(@TempDir tempDir: Path) {
        assertNull(DaemonVersion.read(tempDir.toFile()))
    }
}
