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

/**
 * Tests for AndroidTempFileProvider targeting API level 24 (Nougat),
 * which is below Android O (API 26) to specifically test the pre-O code path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.N]) // API 24 (Nougat)
class AndroidTempFileProviderLowerApiTest {
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
    fun `withTempDirectory creates a temporary directory in cache dir for pre-O API`() {
        var tempDir: File? = null

        tempFileProvider.withTempDirectory("test_prefix") { dir ->
            tempDir = dir
            testFiles.add(dir) // Track for cleanup

            // Verify the directory exists
            assertTrue("Temporary directory should exist", dir.exists())
            assertTrue("Temporary directory should be a directory", dir.isDirectory)

            // Check that the directory is created in the app's cache directory
            assertTrue(
                "Directory should be in cache dir",
                dir.absolutePath.startsWith(context.cacheDir.absolutePath),
            )

            "test_result"
        }

        // Verify the directory is cleaned up after use
        assertFalse("Temporary directory should be deleted after use", tempDir?.exists() ?: true)
    }

    @Test
    fun `withTempDirectory returns result from block in pre-O API`() {
        val result =
            tempFileProvider.withTempDirectory("test_prefix") { dir ->
                testFiles.add(dir) // Track for cleanup
                "expected_result"
            }

        assertEquals("Block result should be returned", "expected_result", result)
    }

    @Test
    fun `withTempDirectory creates files in the directory in pre-O API`() {
        val fileContents = "Test file contents for pre-O API"

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
    fun `withTempDirectory cleans up even when block throws exception in pre-O API`() {
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
    fun `withTempDirectory handles nested files in pre-O API`() {
        val nestedContent = "Nested file content for pre-O API"

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
    fun `withTempDirectory securely deletes files in pre-O API`() {
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

    @Test
    fun `should create multiple temporary directories in pre-O API`() {
        val tempDirs = mutableListOf<File>()
        var checkedCount = 0

        // Create 3 temporary directories
        for (i in 1..3) {
            tempFileProvider.withTempDirectory("test_prefix_$i") { dir ->
                tempDirs.add(dir)
                testFiles.add(dir) // Track for cleanup

                assertTrue("Directory $i should exist", dir.exists())
                assertTrue("Directory $i should be a directory", dir.isDirectory)

                // Create a file in each temp directory
                val testFile = File(dir, "test_file_$i.txt")
                testFile.writeText("Content for file $i")

                assertTrue("Test file $i should exist", testFile.exists())

                checkedCount++
                "success_$i"
            }
        }

        assertEquals("Should have checked 3 directories", 3, checkedCount)

        // All directories should be deleted
        tempDirs.forEach { dir ->
            assertFalse("Directory should be deleted", dir.exists())
        }
    }
}
