package com.selesse.gradle.daemon.platform

import com.selesse.gradle.daemon.DaemonAppExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MacOSHandlerTest {

    @Test
    @EnabledOnOs(OS.MAC)
    fun `plist file is generated with correct structure`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.serviceId.set("com.example.test-daemon")
        extension.jvmArgs.set(listOf("-Xmx512m", "--enable-native-access=ALL-UNNAMED"))
        extension.appArgs.set(listOf("--verbose"))
        extension.keepAlive.set(true)

        val plistPath = tempDir.resolve("test.plist").toFile().absolutePath
        extension.macOS.plistPath = plistPath

        val handler = MacOSHandler()
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        val javaHome = "/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home"

        handler.install(project, extension, jarFile, javaHome, project.logger)

        val plistFile = File(plistPath)
        assertTrue(plistFile.exists(), "Plist file should be created")

        val content = plistFile.readText()

        // Verify key elements
        assertTrue(content.contains("<key>Label</key>"), "Should contain Label key")
        assertTrue(content.contains("<string>com.example.test-daemon</string>"), "Should contain service ID")
        assertTrue(content.contains("<key>ProgramArguments</key>"), "Should contain ProgramArguments key")
        assertTrue(content.contains("<string>$javaHome/bin/java</string>"), "Should contain Java path")
        assertTrue(content.contains("<string>-Xmx512m</string>"), "Should contain JVM args")
        assertTrue(content.contains("<string>--enable-native-access=ALL-UNNAMED</string>"), "Should contain JVM args")
        assertTrue(content.contains("<string>-jar</string>"), "Should contain -jar flag")
        assertTrue(content.contains("<string>${jarFile.absolutePath}</string>"), "Should contain JAR path")
        assertTrue(content.contains("<string>--verbose</string>"), "Should contain app args")
        assertTrue(content.contains("<key>KeepAlive</key>"), "Should contain KeepAlive key")
        assertTrue(content.contains("<true/>"), "Should have KeepAlive set to true")
        assertTrue(content.contains("<key>StandardOutPath</key>"), "Should contain StandardOutPath")
        assertTrue(content.contains("<key>StandardErrorPath</key>"), "Should contain StandardErrorPath")
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `plist with keepAlive false`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        project.pluginManager.apply("com.selesse.daemon-app")

        val extension = project.extensions.getByType(DaemonAppExtension::class.java)
        extension.serviceId.set("com.example.test-daemon")
        extension.keepAlive.set(false)

        val plistPath = tempDir.resolve("test.plist").toFile().absolutePath
        extension.macOS.plistPath = plistPath

        val handler = MacOSHandler()
        val jarFile = tempDir.resolve("test-daemon.jar").toFile()
        jarFile.createNewFile()

        handler.install(project, extension, jarFile, "/path/to/java", project.logger)

        val content = File(plistPath).readText()
        assertTrue(content.contains("<false/>"), "Should have KeepAlive set to false")
    }
}
