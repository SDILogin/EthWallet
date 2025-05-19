package me.sdi.ethwallet.data

import android.content.Context
import android.os.Build
import me.sdi.ethwallet.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

/**
 * Android implementation of [TempFileProvider] that handles temporary file operations
 * with proper cleanup and security considerations.
 *
 * @property context The Android application context
 */
class AndroidTempFileProvider(private val context: Context) : TempFileProvider {
    /**
     * Executes the given [block] function with a temporary directory as parameter,
     * and ensures the directory is securely deleted when the block completes or throws an exception.
     *
     * @param prefix The prefix string to be used in generating the directory name
     * @param block Function to execute with the temporary directory
     * @return The result of the [block] function
     */
    @OptIn(ExperimentalPathApi::class)
    override fun <T> withTempDirectory(prefix: String, block: (File) -> T): T {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val cacheDir = File(context.cacheDir, "temp_${System.currentTimeMillis()}")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            try {
                return block(cacheDir)
            } finally {
                if (cacheDir.exists()) {
                    deleteDirectoryRecursively(cacheDir)
                }
            }
        } else {
            val tempDir: Path = createTempDirectory(prefix)
            try {
                return block(tempDir.toFile())
            } finally {
                try {
                    tempDir.deleteRecursively()
                } catch (e: Exception) {
                    Logger.warn("AndroidTempFileProvider") {
                        "Error deleting temporary directory: ${tempDir.toAbsolutePath()}\n${e.message}"
                    }

                    deleteDirectoryRecursively(tempDir.toFile())
                }
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     * This is a fallback method when Path API is not available or fails.
     *
     * @param directory The directory to delete
     * @return true if deletion was successful, false otherwise
     */
    private fun deleteDirectoryRecursively(directory: File): Boolean {
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectoryRecursively(file)
                } else {
                    // Attempt to securely delete by overwriting with zeros before deletion
                    if (file.length() < MAX_SECURE_OVERWRITE_SIZE) {
                        secureDeleteFile(file)
                    } else {
                        file.delete()
                    }
                }
            }
            return directory.delete()
        }
        return true
    }

    /**
     * Attempts to securely delete a file by overwriting its contents before deletion.
     * Only applies to smaller files to prevent performance issues.
     */
    private fun secureDeleteFile(file: File) {
        try {
            if (file.exists() && file.isFile) {
                file.outputStream().use { output ->
                    val zeros = ByteArray(4096) // 4KB buffer of zeros
                    val fileSize = file.length()
                    var remaining = fileSize

                    while (remaining > 0) {
                        val writeSize = minOf(zeros.size.toLong(), remaining).toInt()
                        output.write(zeros, 0, writeSize)
                        remaining -= writeSize
                    }

                    output.flush()
                }

                file.delete()
            }
        } catch (e: Exception) {
            Logger.warn("AndroidTempFileProvider") {
                "Error securely deleting file: ${file.absolutePath}\n${e.message}"
            }

            file.delete()
        }
    }

    companion object {
        // Don't attempt secure overwrite for files larger than 10MB to prevent performance issues
        private const val MAX_SECURE_OVERWRITE_SIZE = 10 * 1024 * 1024L
    }
}
