package com.akrishna.docconverter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JobExecutionTest {
    private class Probe {
        val states = mutableListOf<JobState>()
        val failures = mutableListOf<Exception>()
        var busy = true
        var progress = "Converting"
        var cleanups = 0
        suspend fun run(work: suspend () -> JobState) = executeJob(
            onState = { states += it },
            onFailure = { failures += it },
            onFinished = { busy = false; progress = ""; cleanups++ },
            work = work,
        )
        fun assertClean() {
            assertFalse(busy)
            assertEquals("", progress)
            assertEquals(1, cleanups)
        }
    }

    @Test fun successClearsBusyAndProgress() = runBlocking {
        val p = Probe()
        p.run { JobState.SUCCEEDED }
        assertEquals(listOf(JobState.RUNNING, JobState.SUCCEEDED), p.states)
        assertTrue(p.failures.isEmpty())
        p.assertClean()
    }

    @Test fun exceptionIsReportedAndControlsUnlock() = runBlocking {
        val p = Probe()
        val failure = IllegalStateException("injected conversion failure")
        p.run { throw failure }
        assertEquals(listOf(JobState.RUNNING, JobState.FAILED), p.states)
        assertSame(failure, p.failures.single())
        p.assertClean()
    }

    @Test fun cancellationIsRethrownAndNotReportedAsFailure() = runBlocking {
        val p = Probe()
        val cancellation = CancellationException("injected cancellation")
        try {
            p.run { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(JobState.RUNNING, JobState.CANCELLED), p.states)
        assertTrue(p.failures.isEmpty())
        p.assertClean()
    }

    @Test fun partialFailureIsNotSuccess() = runBlocking {
        val p = Probe()
        p.run { JobState.PARTIAL_FAILURE }
        assertEquals(JobState.PARTIAL_FAILURE, p.states.last())
        p.assertClean()
    }

    @Test fun collectedFailureClearsBusyAndProgress() = runBlocking {
        val p = Probe()
        p.run { JobState.FAILED }
        assertEquals(JobState.FAILED, p.states.last())
        p.assertClean()
    }

    @Test fun anotherJobCanSucceedAfterFailure() = runBlocking {
        val p = Probe()
        p.run { throw IllegalArgumentException("first job") }
        p.assertClean()
        p.busy = true
        p.progress = "Retry"
        p.run { JobState.SUCCEEDED }
        assertEquals(listOf(JobState.RUNNING, JobState.FAILED, JobState.RUNNING, JobState.SUCCEEDED), p.states)
        assertFalse(p.busy)
        assertEquals("", p.progress)
        assertEquals(2, p.cleanups)
    }
}
