import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
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

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))

                // Swing-backed Dispatchers.Main for the desktop JVM
                implementation(libs.kotlinx.coroutines.swing)

                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }
    }
}

// kotlinx-coroutines-android leaks onto the desktop classpath transitively and its
// high-priority Dispatchers.Main factory references android.os.Looper, which crashes
// on the desktop JVM. Keep it off the desktop runtime so the Swing dispatcher wins.
configurations.all {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
}

compose.desktop {
    application {
        mainClass = "me.sdi.ethwallet.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EthWallet"
            packageVersion = "1.0.0"
        }
    }
}
