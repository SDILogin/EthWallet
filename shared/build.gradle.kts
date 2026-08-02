import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.sdi.ethwallet.resources"
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
        freeCompilerArgs.add("-Xreturn-value-checker=check")
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
    android {
        namespace = "me.sdi.ethwallet.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // The KMP library plugin disables resource processing by default, which drops
        // Compose Multiplatform resources (packaged as Android assets) from the AAR/APK.
        androidResources {
            enable = true
        }

        withHostTest {}
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                // kmp
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.resources)
                implementation(libs.jetbrains.ui.tooling.preview)

                // Ktor
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)

                // Room
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.datastore.preferences.core)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

                // Navigation 3 KMP
                implementation(libs.navigation3.ui.kmp)
                implementation(libs.lifecycle.viewmodel.navigation3.kmp)

                // Adaptive Layouts
                implementation(libs.compose.adaptive)
                implementation(libs.compose.adaptive.layout)
                implementation(libs.compose.adaptive.navigation)
                implementation(libs.compose.adaptive.navigation3)

                // Web3j
                implementation(libs.web3j.core)
            }
        }
        val commonTest = getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidHostTest = getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.robolectric)
            }
        }
        val androidMain = getByName("androidMain") {
            dependencies {
                // Previews
                api(libs.androidx.ui.tooling)
                api(libs.androidx.ui.tooling.preview)

                // Ktor
                implementation(libs.ktor.client.okhttp)

                // Koin
                implementation(libs.koin.android)

                // DataStore
                implementation(libs.androidx.datastore.preferences)

                // Room
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.ktx)

                implementation(libs.androidx.biometric)

                // QR Code
                implementation(libs.zxing.core)
            }
        }
        val desktopMain = getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)

                // QR Code
                implementation(libs.zxing.core)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("commonMainApi", platform(libs.koin.bom))
}

tasks.matching { it.name.startsWith("compileTestKotlinDesktop") || it.name.startsWith("desktopTest") }.configureEach {
    enabled = false
}
