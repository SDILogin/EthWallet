// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("**/*.kt")
            ktlint(libs.versions.ktlint.get())
        }
        kotlinGradle {
            target("**/*.kts")
            ktlint(libs.versions.ktlint.get())
        }
        format("xml") {
            target("**/*.xml")
        }
    }
}