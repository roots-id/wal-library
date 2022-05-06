plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("maven-publish")
}

group = "com.rootsid.wal"
version = "1.0.1-SNAPSHOT"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("org.litote.kmongo:kmongo:4.5.0")

    // needed for cryptography primitives implementation
    implementation("io.iohk.atala:prism-crypto:v1.3.3")
    implementation("io.iohk.atala:prism-identity:v1.3.3")
    implementation("io.iohk.atala:prism-credentials:v1.3.3")
    implementation("io.iohk.atala:prism-api:v1.3.3")

    // Fixes a build issue
    implementation("com.soywiz.korlibs.krypto:krypto-jvm:2.0.6")

    // QR Code
    implementation("org.boofcv:boofcv-core:0.40.1")
    implementation("org.boofcv:boofcv-swing:0.40.1")
    implementation("org.boofcv:boofcv-kotlin:0.40.1")
    implementation("org.boofcv:boofcv-WebcamCapture:0.40.1")

    // DIDComm
    implementation("org.didcommx:didcomm:0.3.0")
    implementation("org.didcommx:peerdid:0.3.0")

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
                username = System.getenv("GITHUB_USER")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}
