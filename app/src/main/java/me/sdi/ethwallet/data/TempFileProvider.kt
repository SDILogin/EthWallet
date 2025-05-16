package me.sdi.ethwallet.data

import java.io.File

/**
 * Interface for providing temporary file operations in a secure manner.
 * Implementation should handle proper creation and cleanup of temporary files.
 */
interface TempFileProvider {
    /**
     * Executes the given [block] function with a temporary directory as parameter,
     * and ensures the directory is deleted when the block completes or throws an exception.
     *
     * @param prefix The prefix string to be used in generating the directory name
     * @param block Function to execute with the temporary directory
     * @return The result of the [block] function
     */
    fun <T> withTempDirectory(prefix: String, block: (File) -> T): T
}
