plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.selesse.gradle"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
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

tasks.named<Test>("test") {
    useJUnitPlatform()
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
