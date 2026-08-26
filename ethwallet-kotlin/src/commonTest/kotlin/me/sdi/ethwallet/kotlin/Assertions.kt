package me.sdi.ethwallet.kotlin

import kotlin.test.fail

internal suspend inline fun <reified T : Throwable> assertSuspendFailsWith(
    message: String? = null,
    block: suspend () -> Unit,
): T {
    val prefix = message?.let { "$it. " }.orEmpty()
    try {
        block()
    } catch (throwable: Throwable) {
        if (throwable is T) return throwable
        fail("${prefix}Expected ${T::class.simpleName}, but caught ${throwable::class.simpleName}")
    }
    fail("${prefix}Expected ${T::class.simpleName}, but no exception was thrown")
}
