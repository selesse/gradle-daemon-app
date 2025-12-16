plugins {
    kotlin("jvm") version "2.3.0-RC"
    application
    id("com.gradleup.shadow") version "9.2.2"
    id("com.selesse.daemon-app")
}

group = "com.selesse.examples"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.methvin:directory-watcher:0.19.1")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation(kotlin("stdlib"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.selesse.filewatcher.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.selesse.filewatcher.MainKt"
    }
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "com.selesse.filewatcher.MainKt"
    }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

daemonApp {
    serviceId.set("file-watcher")
    jarTask.set(tasks.shadowJar)
    // Watch the git root directory (two levels up from this example)
    appArgs.set(listOf(rootDir.parentFile.parentFile.absolutePath))
    // Enable native access for JNA (used by directory-watcher)
    jvmArgs.set(listOf("--enable-native-access=ALL-UNNAMED"))
}
