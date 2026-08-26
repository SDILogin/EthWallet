package me.sdi.ethwallet.flow.e2e.environment

import java.io.File
import java.util.Properties

internal class SepoliaE2eSeeds private constructor(
    val seed1: String,
    val seed2: String,
) {
    override fun toString(): String = "SepoliaE2eSeeds(redacted)"

    companion object {
        fun load(): SepoliaE2eSeeds {
            val localPropertiesPath = checkNotNull(System.getProperty(LOCAL_PROPERTIES_PATH_PROPERTY)) {
                "The Sepolia E2E local.properties path was not configured"
            }
            val properties = Properties().apply {
                File(localPropertiesPath).inputStream().use(::load)
            }
            val seed1 = properties.requiredSecret(SEED_1_KEY)
            val seed2 = properties.requiredSecret(SEED_2_KEY)
            check(seed1 != seed2) { "Sepolia E2E seed phrases must identify different accounts" }
            return SepoliaE2eSeeds(seed1 = seed1, seed2 = seed2)
        }
    }
}

private fun Properties.requiredSecret(key: String): String =
    getProperty(key)?.takeIf(String::isNotBlank)
        ?: error("Missing required Sepolia E2E property: $key")

internal const val LOCAL_PROPERTIES_PATH_PROPERTY = "ethwallet.e2e.localProperties"
private const val SEED_1_KEY = "seed_1"
private const val SEED_2_KEY = "seed_2"
