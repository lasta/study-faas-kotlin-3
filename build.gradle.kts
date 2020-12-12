val ktor_version: String by project

plugins {
    kotlin("multiplatform") version "1.4.20"
}

group = "me.lasta"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
        }
        @kotlin.Suppress("UNUSED_VARIABLE")
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.1")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-curl:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("io.ktor:ktor-client-json:$ktor_version")
                implementation("io.ktor:ktor-client-serialization:$ktor_version")
            }
        }

        @kotlin.Suppress("UNUSED_VARIABLE")
        val nativeTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-mock:$ktor_version")
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "6.7.1"
        distributionType = Wrapper.DistributionType.ALL
    }
}


