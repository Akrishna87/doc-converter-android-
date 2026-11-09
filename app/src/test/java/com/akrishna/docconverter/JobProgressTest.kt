package com.akrishna.docconverter

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class JobProgressTest {
    @Test fun backgroundProgressUsesCallerThreadAndDrainsBeforeReturn() = runBlocking {
        withTimeout(5000) {
            val caller = Thread.currentThread()
            val seen = mutableListOf<Pair<Int, Int>>()
            val result = withJobProgress(
                onProgress = { i, n ->
                    assertSame(caller, Thread.currentThread())
                    seen += i to n
                },
            ) { report ->
                withContext(Dispatchers.Default) {
                    repeat(100) { report(it, 100) }
                    42
                }
            }
            assertEquals(42, result)
            assertEquals(99 to 100, seen.last())
        }
    }

    @Test fun callbackAfterSuccessCannotRestoreClearedProgress() = runBlocking {
        var late: ((Int, Int) -> Unit)? = null
        var progress = ""
        withJobProgress(onProgress = { i, _ -> progress = i.toString() }) { report ->
            late = report
            report(1, 2)
        }
        progress = ""
        late!!(2, 2)
        yield()
        assertEquals("", progress)
    }

    @Test fun callbackAfterFailureCannotRestoreClearedProgress() = runBlocking {
        var late: ((Int, Int) -> Unit)? = null
        var progress = "Converting"
        val expected = IllegalStateException("injected failure")
        try {
            withJobProgress<Unit>(onProgress = { i, _ -> progress = i.toString() }) { report ->
                late = report
                report(1, 2)
                throw expected
            }
            fail("Failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        progress = ""
        late!!(2, 2)
        yield()
        assertEquals("", progress)
    }
}
