import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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
        getByName("desktopTest") {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.androidx.datastore.preferences.core)
                implementation(libs.junit)
            }
        }
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
            targetFormats(TargetFormat.Dmg)
            packageName = "EthWallet"
            packageVersion = "1.0.0"
        }
    }
}

val sepoliaE2eEnabled = providers.gradleProperty("sepoliaE2e")
    .map { it == "true" }
    .orElse(false)
val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val sepoliaE2eCategory = "me.sdi.ethwallet.desktop.e2e.SepoliaE2e"
val localPropertiesPathProperty = "ethwallet.e2e.localProperties"

fun commandSucceeds(vararg command: String): Boolean {
    val process = ProcessBuilder(*command)
        .directory(rootProject.projectDir)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    return process.waitFor() == 0
}

val validateSepoliaE2eEnvironment = tasks.register("validateSepoliaE2eEnvironment") {
    group = "verification"
    description = "Validates the local, secret-backed Sepolia E2E environment."
    outputs.upToDateWhen { false }
    doLast {
        check(sepoliaE2eEnabled.get()) {
            "Sepolia E2E is disabled. Run with -PsepoliaE2e=true."
        }
        check(System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)) {
            "Sepolia E2E desktop tests require Apple Silicon macOS."
        }
        check(System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) {
            "Sepolia E2E desktop tests require Apple Silicon macOS."
        }
        check(localPropertiesFile.asFile.isFile) {
            "Missing root local.properties."
        }
        check(commandSucceeds("git", "check-ignore", "--quiet", "--", "local.properties")) {
            "Root local.properties must be ignored by Git."
        }
        check(!commandSucceeds("git", "ls-files", "--error-unmatch", "--", "local.properties")) {
            "Root local.properties must not be tracked by Git."
        }
        val properties = Properties().apply {
            localPropertiesFile.asFile.inputStream().use(::load)
        }
        check(properties.getProperty("seed_1")?.isNotBlank() == true) {
            "Missing required Sepolia E2E property: seed_1"
        }
        check(properties.getProperty("seed_2")?.isNotBlank() == true) {
            "Missing required Sepolia E2E property: seed_2"
        }
        check(properties.getProperty("seed_1") != properties.getProperty("seed_2")) {
            "Sepolia E2E seed phrases must identify different accounts."
        }
        logger.lifecycle("Sepolia E2E environment is configured.")
    }
}

tasks.named<Test>("desktopTest") {
    useJUnit { excludeCategories(sepoliaE2eCategory) }
}

tasks.register<Test>("sepoliaE2eTest") {
    group = "verification"
    description = "Runs the opt-in desktop Sepolia wallet transfer E2E test."
    dependsOn(validateSepoliaE2eEnvironment, "desktopTestClasses")
    val desktopTest = tasks.named<Test>("desktopTest")
    testClassesDirs = desktopTest.get().testClassesDirs
    classpath = desktopTest.get().classpath
    useJUnit { includeCategories(sepoliaE2eCategory) }
    systemProperty(localPropertiesPathProperty, localPropertiesFile.asFile.absolutePath)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
