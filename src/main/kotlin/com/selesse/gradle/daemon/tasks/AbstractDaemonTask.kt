package com.selesse.gradle.daemon.tasks

import com.selesse.gradle.daemon.platform.DaemonRequest
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import java.io.File

/**
 * Common config-cache-safe inputs shared by all `daemonX` tasks. Values are wired from
 * `DaemonAppExtension`'s providers at configuration time (see `DaemonAppPlugin.wireFrom`), so
 * `@TaskAction` implementations never need to reach back into `Project`/`DaemonAppExtension`.
 */
abstract class AbstractDaemonTask : DefaultTask() {
    @get:Input
    abstract val serviceId: Property<String>

    @get:Input
    @get:Optional
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val appArgs: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val keepAlive: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val trackVersion: Property<Boolean>

    // Not @InputFile: this directory legitimately doesn't exist yet for daemonStatus/daemonLogs/
    // daemonUninstall etc. — only daemonInstall creates it.
    @get:Internal
    abstract val releaseDir: RegularFileProperty

    @get:Internal
    abstract val logFile: RegularFileProperty

    @get:Internal
    abstract val projectDir: Property<File>

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:Internal
    abstract val javaHome: Property<String>

    @get:Internal
    abstract val macOSPlistPath: Property<String>

    @get:Internal
    abstract val linuxUserService: Property<Boolean>

    @get:Internal
    abstract val linuxServicePath: Property<String>

    @get:Internal
    abstract val windowsBackend: Property<String>

    @get:Internal
    abstract val windowsWinswExecutable: Property<String>

    @get:Internal
    abstract val windowsServiceDisplayName: Property<String>

    @get:Internal
    abstract val windowsServiceDescription: Property<String>

    protected fun daemonRequest(): DaemonRequest {
        return DaemonRequest(
            serviceId = serviceId.get(),
            jvmArgs = jvmArgs.getOrElse(emptyList()),
            appArgs = appArgs.getOrElse(emptyList()),
            keepAlive = keepAlive.getOrElse(true),
            releaseDir = releaseDir.orNull?.asFile,
            logFile = logFile.orNull?.asFile,
            projectDir = projectDir.get(),
            jarFile = jarFile.get().asFile,
            javaHome = javaHome.get(),
            macOSPlistPath = macOSPlistPath.orNull,
            linuxUserService = linuxUserService.getOrElse(true),
            linuxServicePath = linuxServicePath.orNull,
            windowsBackend = windowsBackend.get(),
            windowsWinswExecutable = windowsWinswExecutable.orNull,
            windowsServiceDisplayName = windowsServiceDisplayName.orNull,
            windowsServiceDescription = windowsServiceDescription.orNull,
        )
    }
}
