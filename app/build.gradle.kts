import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    // Web3j
    implementation(libs.web3j.core)

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
