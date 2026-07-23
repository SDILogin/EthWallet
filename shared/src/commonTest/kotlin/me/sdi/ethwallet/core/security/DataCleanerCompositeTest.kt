package me.sdi.ethwallet.core.security

import kotlinx.coroutines.test.runTest
import me.sdi.ethwallet.fakes.FakeDataCleaner
import kotlin.test.Test
import kotlin.test.assertEquals

class DataCleanerCompositeTest {
    @Test
    fun clean_runs_every_cleaner() = runTest {
        val first = FakeDataCleaner()
        val second = FakeDataCleaner()

        DataCleanerComposite(listOf(first, second)).clean()

        assertEquals(1, first.cleanCalledCount, "First cleaner should run")
        assertEquals(1, second.cleanCalledCount, "Second cleaner should run")
    }

    @Test
    fun clean_continues_when_one_cleaner_fails() = runTest {
        val failing = FakeDataCleaner().also { it.shouldThrowException = true }
        val healthy = FakeDataCleaner()

        DataCleanerComposite(listOf(failing, healthy)).clean()

        assertEquals(1, healthy.cleanCalledCount, "Healthy cleaner should still run when another fails")
    }
}
