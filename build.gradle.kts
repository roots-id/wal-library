plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("maven-publish")
}

group = "com.rootsid.wal"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    // Required to resolve com.soywiz.korlibs.krypto:krypto-jvm:2.0.6
    maven("https://plugins.gradle.org/m2/")
    maven {
        url = uri("https://maven.pkg.github.com/input-output-hk/better-parse")
        credentials {
            username = System.getenv("PRISM_SDK_USER")
            password = System.getenv("PRISM_SDK_PASSWORD")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    implementation("org.litote.kmongo:kmongo:4.4.0")

    // needed for cryptography primitives implementation
    implementation("io.iohk.atala:prism-crypto:1.2.0")
    implementation("io.iohk.atala:prism-identity:1.2.0")
    implementation("io.iohk.atala:prism-credentials:1.2.0")
    implementation("io.iohk.atala:prism-api:1.2.0")

    // Fixes a build issue
    implementation("com.soywiz.korlibs.krypto:krypto-jvm:2.0.6")

    // QR Code
    implementation("org.boofcv:boofcv-core:0.39.1")
    implementation("org.boofcv:boofcv-swing:0.39.1")
    implementation("org.boofcv:boofcv-kotlin:0.39.1")
    implementation("org.boofcv:boofcv-WebcamCapture:0.39.1")

    // DIDComm
    implementation("org.didcommx:didcomm:0.3.0")
    implementation("org.didcommx:peerdid:0.2.0")

    implementation("org.junit.jupiter:junit-jupiter:5.8.2")
}

publishing {
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
            artifact(tasks["kotlinSourcesJar"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/roots-id/wal-library")
            credentials {
                username = System.getenv("ROOTS-ID_USER")
                password = System.getenv("ROOTS-ID_PASSWORD")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}
