package me.sdi.ethwallet.core

import me.sdi.ethwallet.data.TempFileProvider
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

/**
 * A fake implementation of TempFileProvider for testing purposes.
 * Uses Kotlin's standard library temp file APIs instead of Android-specific ones.
 */
class FakeTempFileProvider : TempFileProvider {
    // Track created directories for cleanup
    private val createdDirs = mutableListOf<File>()

    /**
     * Executes the given [block] function with a temporary directory as parameter,
     * and ensures the directory is deleted when the block completes or throws an exception.
     *
     * @param prefix The prefix string to be used in generating the directory name
     * @param block Function to execute with the temporary directory
     * @return The result of the [block] function
     */
    @OptIn(ExperimentalPathApi::class)
    override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
        val tempDir = createTempDirectory(prefix).toFile()
        createdDirs.add(tempDir)

        try {
            return block(tempDir)
        } finally {
            cleanupDir(tempDir)
        }
    }

    /**
     * Cleans up a directory by deleting it recursively.
     *
     * @param dir The directory to clean up
     */
    @OptIn(ExperimentalPathApi::class)
    private fun cleanupDir(dir: File) {
        try {
            if (dir.exists()) {
                val path = dir.toPath()
                path.deleteRecursively()
            }
        } catch (e: Exception) {
            // Fallback to regular deletion if deleteRecursively fails
            dir.deleteRecursively()
        } finally {
            createdDirs.remove(dir)
        }
    }

    /**
     * Cleans up any remaining directories.
     * Should be called in test tearDown methods.
     */
    fun cleanupAll() {
        createdDirs.toList().forEach { cleanupDir(it) }
    }
}
