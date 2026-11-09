package com.akrishna.docconverter

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Deliver progress on the caller context; keep only the latest pending event. */
internal suspend fun <T> withJobProgress(
    onProgress: (Int, Int) -> Unit,
    work: suspend ((Int, Int) -> Unit) -> T,
): T = coroutineScope {
    val events = Channel<Pair<Int, Int>>(Channel.CONFLATED)
    val reader = launch {
        for ((index, total) in events) onProgress(index, total)
    }
    try {
        val result = work { index, total -> events.trySend(index to total); Unit }
        events.close()
        reader.join()
        result
    } finally {
        events.close()
        reader.cancel()
    }
}
