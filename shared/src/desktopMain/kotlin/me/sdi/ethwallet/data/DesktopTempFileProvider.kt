package me.sdi.ethwallet.data

import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

class DesktopTempFileProvider : TempFileProvider {
    @OptIn(ExperimentalPathApi::class)
    override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
        val tempDir = createTempDirectory(prefix)
        return try {
            block(tempDir.toFile())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
