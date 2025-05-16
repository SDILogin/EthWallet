package me.sdi.ethwallet.blockchain

import android.content.Context
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P]) // API 28
class AndroidTempFileProviderTest {
    private lateinit var context: Context
    private lateinit var tempFileProvider: AndroidTempFileProvider
    private val testFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tempFileProvider = AndroidTempFileProvider(context)
    }

    @After
    fun tearDown() {
        // Clean up any test files that might have been created
        testFiles.forEach { file ->
            if (file.exists()) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }
        testFiles.clear()
    }

    @Test
    fun `withTempDirectory creates a temporary directory`() {
        var tempDir: File? = null

        tempFileProvider.withTempDirectory("test_prefix") { dir ->
            tempDir = dir
            testFiles.add(dir) // Track for cleanup

            // Verify the directory exists
            assertTrue("Temporary directory should exist", dir.exists())
            assertTrue("Temporary directory should be a directory", dir.isDirectory)
        }

        // Verify the directory is cleaned up after use
        assertFalse("Temporary directory should be deleted after use", tempDir?.exists() ?: true)
    }

    @Test
    fun `withTempDirectory returns result from block`() {
        val result =
            tempFileProvider.withTempDirectory("test_prefix") { dir ->
                testFiles.add(dir) // Track for cleanup

                "expected_result"
            }

        assertEquals("Block result should be returned", "expected_result", result)
    }

    @Test
    fun `withTempDirectory creates files in the directory`() {
        val fileContents = "Test file contents"

        var checked = false
        tempFileProvider.withTempDirectory("test_prefix") { dir ->
            testFiles.add(dir) // Track for cleanup

            // Create a file in the temp directory
            val testFile = File(dir, "test_file.txt")
            testFile.writeText(fileContents)

            // Verify the file exists and has the correct contents
            assertTrue("Test file should exist", testFile.exists())
            assertEquals("File should have correct contents", fileContents, testFile.readText())

            checked = true
        }

        assertTrue("Looks like we didn't check the files", checked)
    }

    @Test
    fun `withTempDirectory cleans up even when block throws exception`() {
        var tempDir: File? = null

        try {
            tempFileProvider.withTempDirectory("test_prefix") { dir ->
                tempDir = dir
                testFiles.add(dir) // Track for cleanup

                // Throw an exception to test cleanup
                throw RuntimeException("Test exception")
            }
        } catch (e: RuntimeException) {
            // Expected exception, continue
        }

        // Verify the directory is cleaned up after exception
        assertFalse("Temporary directory should be deleted after exception", tempDir?.exists() ?: true)
    }

    @Test
    fun `withTempDirectory handles nested files`() {
        val nestedContent = "Nested file content"

        var checked = false
        tempFileProvider.withTempDirectory("test_prefix") { dir ->
            testFiles.add(dir) // Track for cleanup

            // Create a nested directory structure
            val nestedDir = File(dir, "nested_dir")
            assertTrue("Should be able to create nested directory", nestedDir.mkdir())

            // Create a file in the nested directory
            val nestedFile = File(nestedDir, "nested_file.txt")
            nestedFile.writeText(nestedContent)

            assertTrue("Nested directory should exist", nestedDir.exists())
            assertTrue("Nested file should exist", nestedFile.exists())
            assertEquals("Nested file should have correct contents", nestedContent, nestedFile.readText())
            checked = true
        }

        assertTrue("Looks like we didn't check the files", checked)
    }

    @Test
    fun `withTempDirectory securely deletes files`() {
        // This test is more of a functional test than a unit test
        // since we can't easily verify the secure deletion in a test environment
        // But we can at least verify the method doesn't throw exceptions

        val tempFile = File.createTempFile("test", ".tmp")
        testFiles.add(tempFile)

        var checked = false
        tempFileProvider.withTempDirectory("test_prefix") { dir ->
            testFiles.add(dir)

            // Create a small file (to be securely deleted)
            val smallFile = File(dir, "small_file.txt")
            smallFile.writeText("Small file content")

            // Create a large file (over the secure delete threshold)
            val largeFile = File(dir, "large_file.txt")
            // Write a 15MB file
            largeFile.outputStream().use { out ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                for (i in 0 until 15) {
                    out.write(buffer)
                }
            }

            assertTrue("Small file should exist", smallFile.exists())
            assertTrue("Large file should exist", largeFile.exists())

            checked = true
        }

        assertTrue("Looks like we didn't check the files", checked)
    }
}
