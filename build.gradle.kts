plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("org.jetbrains.changelog") version "2.2.1"
    id("pl.allegro.tech.build.axion-release") version "1.18.2"
}

group = "com.selesse.gradle"
version = scmVersion.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation(gradleTestKit())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website.set("https://github.com/selesse/gradle-daemon-app")
    vcsUrl.set("https://github.com/selesse/gradle-daemon-app")

    plugins {
        create("daemonApp") {
            id = "com.selesse.daemon-app"
            implementationClass = "com.selesse.gradle.daemon.DaemonAppPlugin"
            displayName = "Daemon App Plugin"
            description = "Gradle plugin for installing and managing Java applications as background daemons"
            tags.set(listOf("daemon", "background", "service", "launchd", "systemd"))
        }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.1")
        }
    }
}

spotless {
    kotlin {
        ktlint("1.0.1").editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
            ),
        )
        target("src/**/*.kt")
    }
    kotlinGradle {
        ktlint("1.0.1")
    }
}

if (System.getenv("CI") == null) {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        dependsOn("spotlessApply")
    }
}

scmVersion {
    tag {
        prefix.set("v")
    }
    nextVersion {
        suffix.set("SNAPSHOT")
        separator.set("-")
    }
}

changelog {
    version.set(project.version.toString())
    path.set(file("CHANGELOG.md").canonicalPath)
    header.set(provider { "${project.version}" })
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    combinePreReleases.set(false)
}
