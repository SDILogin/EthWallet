import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.spotless)
}

val rustDirectory = layout.projectDirectory.dir("rust")
val rustManifest = rustDirectory.file("Cargo.toml")
val rustLockfile = rustDirectory.file("Cargo.lock")
val rustToolchain = rustDirectory.file("rust-toolchain.toml")
val rustSourceDirectory = rustDirectory.dir("src")
val rustIncludeDirectory = rustDirectory.dir("include")
val rustTargetDirectory = layout.buildDirectory.dir("rust/target")
val generatedNativeDirectory = layout.buildDirectory.dir("generated/rust")

val nativeLibraryName = "ethwallet_crypto"
val androidNdkVersion = "30.0.14904198"

abstract class RustNativeLibrarySync : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        into(outputDirectory)
    }
}

val androidSdkDirectory = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(
        layout.projectDirectory.file("../local.properties").asFile.let { localProperties ->
            providers.provider {
                localProperties
                    .takeIf { it.isFile }
                    ?.readLines()
                    ?.firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter("sdk.dir=")
            }
        },
    )
val androidNdkDirectory = androidSdkDirectory.map { sdkDirectory ->
    file("$sdkDirectory/ndk/$androidNdkVersion")
}

fun cargoInputs(task: Exec) {
    task.inputs.file(rustManifest)
    task.inputs.file(rustLockfile)
    task.inputs.file(rustToolchain)
    task.inputs.dir(rustSourceDirectory)
    task.inputs.dir(rustIncludeDirectory)
}

fun registerCargoBuild(
    taskName: String,
    target: String,
    libraryFileName: String,
): TaskProvider<Exec> =
    tasks.register<Exec>(taskName) {
        group = "rust"
        description = "Builds $nativeLibraryName for $target."
        workingDir(rustDirectory)
        commandLine(
            "cargo",
            "build",
            "--manifest-path",
            rustManifest.asFile.absolutePath,
            "--target",
            target,
            "--target-dir",
            rustTargetDirectory.get().asFile.absolutePath,
            "--release",
        )
        cargoInputs(this)
        outputs.file(
            rustTargetDirectory.map { directory ->
                directory.file("$target/release/$libraryFileName")
            },
        )
        outputs.file(
            rustTargetDirectory.map { directory ->
                directory.file("$target/release/lib$nativeLibraryName.a")
            },
        )
    }

val buildRustAndroid = tasks.register<Exec>("buildRustAndroid") {
    group = "rust"
    description = "Builds $nativeLibraryName for Android arm64-v8a and x86_64."
    workingDir(rustDirectory)
    commandLine(
        "cargo",
        "ndk",
        "-t",
        "arm64-v8a",
        "-t",
        "x86_64",
        "-p",
        "24",
        "-o",
        generatedNativeDirectory.get().dir("android/jniLibs").asFile.absolutePath,
        "build",
        "--release",
    )
    cargoInputs(this)
    inputs.property("androidNdkVersion", androidNdkVersion)
    inputs.property("androidNdkPath", androidNdkDirectory.map { it.absolutePath })
    outputs.dir(generatedNativeDirectory.map { it.dir("android/jniLibs") })
    doFirst {
        val ndkDirectory = androidNdkDirectory.get()
        check(ndkDirectory.isDirectory) {
            "Android NDK $androidNdkVersion is required at $ndkDirectory."
        }
        environment("ANDROID_NDK_HOME", ndkDirectory.absolutePath)
        environment("CARGO_TARGET_DIR", rustTargetDirectory.get().asFile.absolutePath)
    }
}

val buildRustMacosArm64 = registerCargoBuild(
    taskName = "buildRustMacosArm64",
    target = "aarch64-apple-darwin",
    libraryFileName = "lib$nativeLibraryName.dylib",
)
val buildRustIosArm64 = registerCargoBuild(
    taskName = "buildRustIosArm64",
    target = "aarch64-apple-ios",
    libraryFileName = "lib$nativeLibraryName.a",
)
val buildRustIosSimulatorArm64 = registerCargoBuild(
    taskName = "buildRustIosSimulatorArm64",
    target = "aarch64-apple-ios-sim",
    libraryFileName = "lib$nativeLibraryName.a",
)
val buildRustIosX64 = registerCargoBuild(
    taskName = "buildRustIosX64",
    target = "x86_64-apple-ios",
    libraryFileName = "lib$nativeLibraryName.a",
)

val packageRustMacosLibraries = tasks.register<Sync>("packageRustMacosLibraries") {
    group = "rust"
    description = "Packages the Apple Silicon macOS Rust library as a JVM resource."
    dependsOn(buildRustMacosArm64)
    from(rustTargetDirectory.map { it.dir("aarch64-apple-darwin/release") }) {
        include("lib$nativeLibraryName.dylib")
        into("native/macos/aarch64")
    }
    into(generatedNativeDirectory.map { it.dir("jvmResources") })
}

val packageRustAndroidLibraries = tasks.register<RustNativeLibrarySync>("packageRustAndroidLibraries") {
    group = "rust"
    description = "Makes Android Rust libraries available to the Android packaging pipeline."
    dependsOn(buildRustAndroid)
    from(generatedNativeDirectory.map { it.dir("android/jniLibs") })
    outputDirectory.set(generatedNativeDirectory.map { it.dir("android/packagedJniLibs") })
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
    android {
        namespace = "me.sdi.ethwallet.kotlin"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest {}

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        if (konanTarget.family.isAppleFamily) {
            val rustBuildTask = when (name) {
                "iosArm64" -> buildRustIosArm64
                "iosSimulatorArm64" -> buildRustIosSimulatorArm64
                "iosX64" -> buildRustIosX64
                else -> return@configureEach
            }
            val rustTarget = when (name) {
                "iosArm64" -> "aarch64-apple-ios"
                "iosSimulatorArm64" -> "aarch64-apple-ios-sim"
                else -> "x86_64-apple-ios"
            }
            val rustArchive = rustTargetDirectory.map { directory ->
                directory.file("$rustTarget/release/lib$nativeLibraryName.a")
            }
            val rustReleaseDirectory = rustTargetDirectory.map { directory ->
                directory.dir("$rustTarget/release")
            }

            compilations.getByName("main").cinterops.create("ethwallet_crypto") {
                defFile(rustDirectory.file("interop/ethwallet_crypto.def").asFile)
                includeDirs.headerFilterOnly(rustIncludeDirectory.asFile)
                compilerOpts("-I${rustIncludeDirectory.asFile.absolutePath}")
                extraOpts(
                    "-libraryPath",
                    rustReleaseDirectory.get().asFile.absolutePath,
                    "-staticLibrary",
                    "lib$nativeLibraryName.a",
                )
            }

            binaries.configureEach {
                linkTaskProvider.configure {
                    dependsOn(rustBuildTask)
                    inputs.file(rustArchive)
                }
            }

            tasks.matching { task ->
                task.name.startsWith("cinteropEthwallet_crypto") && task.name.endsWith(name.replaceFirstChar(Char::uppercase))
            }.configureEach {
                dependsOn(rustBuildTask)
                inputs.file(rustArchive)
            }
        }
    }

    sourceSets {
        named("jvmMain") {
            resources.srcDir(packageRustMacosLibraries)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

val androidComponents = extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>()
androidComponents.onVariants(androidComponents.selector().all()) { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(
        packageRustAndroidLibraries,
        RustNativeLibrarySync::outputDirectory,
    )
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(packageRustMacosLibraries)
}

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    val hostOs = System.getProperty("os.name")
    val hostArchitecture = System.getProperty("os.arch").lowercase()
    val supportedHost = hostOs.equals("Mac OS X", ignoreCase = true) && hostArchitecture in setOf("aarch64", "arm64")
    if (supportedHost) {
        dependsOn(buildRustMacosArm64)
        val nativeDirectory = rustTargetDirectory.map { it.dir("aarch64-apple-darwin/release").asFile }
        doFirst {
            val existing = System.getProperty("java.library.path").orEmpty()
            val combined = listOf(nativeDirectory.get().absolutePath, existing)
                .filter(String::isNotEmpty)
                .joinToString(File.pathSeparator)
            systemProperty("java.library.path", combined)
        }
    } else {
        doFirst {
            throw UnsupportedOperationException("Android host tests require Apple Silicon macOS")
        }
    }
}
