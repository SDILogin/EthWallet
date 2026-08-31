import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

extensions.configure<RoomExtension> {
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

extensions.configure<ApplicationExtension>("android") {
    namespace = "me.sdi.ethwallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.sdi.ethwallet"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "ETH_RPC_URL", "\"https://ethereum-sepolia-rpc.publicnode.com\"")
            buildConfigField("long", "ETH_CHAIN_ID", "11155111L")
            buildConfigField("String", "ETH_EXPLORER_BASE_URL", "\"https://sepolia.etherscan.io\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            buildConfigField("String", "ETH_RPC_URL", "\"https://ethereum-rpc.publicnode.com\"")
            buildConfigField("long", "ETH_CHAIN_ID", "1L")
            buildConfigField("String", "ETH_EXPLORER_BASE_URL", "\"https://etherscan.io\"")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/FastDoubleParser-LICENSE",
                "META-INF/FastDoubleParser-NOTICE",
                "META-INF/io.netty.versions.properties",
                "META-INF/thirdparty-LICENSE",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.resources)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // ZXing QR Code Generation
    implementation(libs.zxing.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // Ktor
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.compose.ui.test)
    testImplementation(libs.androidx.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

val sepoliaE2eEnabled = providers.gradleProperty("sepoliaE2e")
    .map { it == "true" }
    .orElse(false)
val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val sepoliaE2eCategory = "me.sdi.ethwallet.flow.e2e.environment.SepoliaE2e"
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
            "Sepolia E2E Android host tests require Apple Silicon macOS."
        }
        check(System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) {
            "Sepolia E2E Android host tests require Apple Silicon macOS."
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

tasks.withType<Test>().configureEach {
    val runsSepoliaE2e = name == "testDebugUnitTest" && sepoliaE2eEnabled.get()
    useJUnit {
        if (runsSepoliaE2e) {
            includeCategories(sepoliaE2eCategory)
        } else {
            excludeCategories(sepoliaE2eCategory)
        }
    }

    if (runsSepoliaE2e) {
        dependsOn(validateSepoliaE2eEnvironment)
        systemProperty(localPropertiesPathProperty, localPropertiesFile.asFile.absolutePath)

        val hostOs = System.getProperty("os.name")
        val hostArchitecture = System.getProperty("os.arch").lowercase()
        val supportedHost = hostOs.equals("Mac OS X", ignoreCase = true) &&
            hostArchitecture in setOf("aarch64", "arm64")
        if (supportedHost) {
            dependsOn(":ethwallet-kotlin:buildRustMacosArm64")
            val nativeDirectory = rootProject.layout.projectDirectory
                .dir("ethwallet-kotlin/build/rust/target/aarch64-apple-darwin/release")
                .asFile
            val javaLibraryPath = listOf(
                nativeDirectory.absolutePath,
                System.getProperty("java.library.path").orEmpty(),
            ).filter(String::isNotEmpty).joinToString(File.pathSeparator)
            systemProperty("java.library.path", javaLibraryPath)
        } else {
            doFirst {
                error("Sepolia E2E Android host tests require Apple Silicon macOS.")
            }
        }
    }
}

tasks.register("sepoliaE2eTest") {
    group = "verification"
    description = "Runs the opt-in Sepolia wallet transfer E2E test."
    dependsOn(validateSepoliaE2eEnvironment)
    if (sepoliaE2eEnabled.get()) {
        dependsOn("testDebugUnitTest")
    }
    doFirst {
        check(sepoliaE2eEnabled.get()) {
            "Sepolia E2E is disabled. Run with -PsepoliaE2e=true."
        }
    }
}
