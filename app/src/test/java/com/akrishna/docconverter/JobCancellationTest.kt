package com.akrishna.docconverter

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class JobCancellationTest {
    private class Probe {
        val states = mutableListOf<JobState>()
        val failures = mutableListOf<Exception>()
        var busy = true
        var progress = "Converting"
        var cleanups = 0
        var enteredWork = false
        suspend fun execute() = executeJob(
            onState = { states += it },
            onFailure = { failures += it },
            onFinished = { busy = false; progress = ""; cleanups++ },
        ) {
            enteredWork = true
            awaitCancellation()
        }
        fun assertCancelledAndClean() {
            assertEquals(listOf(JobState.RUNNING, JobState.CANCELLED), states)
            assertTrue(failures.isEmpty())
            assertFalse(busy)
            assertEquals("", progress)
            assertEquals(1, cleanups)
        }
    }

    @Test fun cancellingSuspendedJobRunsCleanup() = runBlocking {
        withTimeout(5000) {
            val p = Probe()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { p.execute() }
            assertTrue(p.enteredWork)
            assertTrue(job.isActive)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            p.assertCancelledAndClean()
        }
    }

    @Test fun alreadyCancelledScopeStillEntersCleanupWithUndispatchedStart() = runBlocking {
        withTimeout(5000) {
            val p = Probe()
            val parent = Job().apply { cancel() }
            val scope = CoroutineScope(parent + Dispatchers.Unconfined)
            val job = scope.launch(start = CoroutineStart.UNDISPATCHED) { p.execute() }
            job.join()
            assertTrue(job.isCancelled)
            p.assertCancelledAndClean()
        }
    }

    @Test fun cancellingParentScopeCleansSuspendedChild() = runBlocking {
        withTimeout(5000) {
            val p = Probe()
            val parent = SupervisorJob()
            val scope = CoroutineScope(parent + Dispatchers.Unconfined)
            try {
                val job = scope.launch(start = CoroutineStart.UNDISPATCHED) { p.execute() }
                assertTrue(p.enteredWork)
                parent.cancel()
                job.join()
                assertTrue(job.isCancelled)
                p.assertCancelledAndClean()
            } finally {
                parent.cancel()
            }
        }
    }
}
