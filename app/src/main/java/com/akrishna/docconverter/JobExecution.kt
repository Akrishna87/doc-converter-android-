package com.akrishna.docconverter

import kotlinx.coroutines.CancellationException

enum class JobState { IDLE, RUNNING, SUCCEEDED, PARTIAL_FAILURE, FAILED, CANCELLED }

internal suspend fun executeJob(
    onState: (JobState) -> Unit,
    onFailure: (Exception) -> Unit,
    onFinished: () -> Unit,
    work: suspend () -> JobState,
) {
    try {
        onState(JobState.RUNNING)
        val terminal = work()
        require(terminal == JobState.SUCCEEDED || terminal == JobState.PARTIAL_FAILURE || terminal == JobState.FAILED)
        onState(terminal)
    } catch (cancelled: CancellationException) {
        onState(JobState.CANCELLED)
        throw cancelled
    } catch (failure: Exception) {
        onState(JobState.FAILED)
        onFailure(failure)
    } finally {
        onFinished()
    }
}
