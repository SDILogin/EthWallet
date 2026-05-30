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
    androidLibrary {
        namespace = "me.sdi.ethwallet.shared"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // The KMP library plugin disables resource processing by default, which drops
        // Compose Multiplatform resources (packaged as Android assets) from the AAR/APK.
        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // kmp
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

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
                implementation(libs.androidx.room.ktx)
                implementation(libs.androidx.datastore.preferences.core)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

                // Navigation 3 KMP
                implementation(libs.navigation3.ui.kmp)
                implementation(libs.lifecycle.viewmodel.navigation3.kmp)
            }
        }
        val androidMain by getting {
            dependencies {
                // Ktor
                implementation(libs.ktor.client.okhttp)

                // Koin
                implementation(libs.koin.android)

                // Web3j
                implementation(libs.web3j.core)

                // DataStore
                implementation(libs.androidx.datastore.preferences)

                // Room
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.ktx)

                // QR Code
                implementation(libs.core)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.web3j.core)

                // QR Code
                implementation(libs.core)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("commonMainApi", platform(libs.koin.bom))
}
