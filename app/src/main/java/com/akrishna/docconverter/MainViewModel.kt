package com.akrishna.docconverter

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var sources by mutableStateOf<List<Src>>(emptyList()); private set
    var target by mutableStateOf<Fmt?>(null); private set
    var options by mutableStateOf(Options()); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var results by mutableStateOf<List<Output>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var jobState by mutableStateOf(JobState.IDLE); private set

    val targets: List<Fmt> get() = commonTargets(sources.map { it.fmt })
    val allPdf: Boolean get() = sources.isNotEmpty() && sources.all { it.fmt == Fmt.PDF }
    val anyTextLike: Boolean get() = sources.any { it.fmt.isTextLike }
    val anyPdf: Boolean get() = sources.any { it.fmt == Fmt.PDF }

    /** Ignore incoming picker/share results while a conversion owns the selection. */
    fun setFiles(uris: List<Uri>, append: Boolean) {
        if (busy) return
        val cr = getApplication<Application>().contentResolver
        val list = if (append) sources.toMutableList() else mutableListOf()
        val skipped = mutableListOf<String>()
        for (uri in uris) {
            if (list.any { it.uri == uri }) continue
            val name = Converter.displayName(cr, uri)
            val fmt = detectFormat(cr.getType(uri), name)
            if (fmt == null) skipped += name else list += Src(uri, name, fmt)
        }
        sources = list
        results = emptyList()
        jobState = JobState.IDLE
        error = if (skipped.isEmpty()) null else "Unsupported file type: ${skipped.joinToString()}"
        if (target !in targets) target = targets.firstOrNull()
    }

    fun remove(src: Src) {
        if (busy) return
        sources = sources - src
        results = emptyList()
        jobState = JobState.IDLE
        if (target !in targets) target = targets.firstOrNull()
    }

    fun clear() {
        if (busy) return
        sources = emptyList(); target = null; results = emptyList(); error = null
        jobState = JobState.IDLE
    }

    fun selectTarget(f: Fmt) {
        if (!busy && f in targets) target = f
    }

    fun update(f: (Options) -> Options) {
        if (!busy) options = f(options)
    }

    fun convert() {
        if (busy) return
        val to = target ?: return
        val inputs = ConversionInputs(sources, to, options)
        val srcs = inputs.sources
        if (srcs.isEmpty() || inputs.target !in commonTargets(srcs.map { it.fmt })) return
        val app = getApplication<Application>()
        busy = true
        progress = "Converting…"
        error = null
        results = emptyList()
        // Enter cleanup ownership immediately, even if the scope is already cancelled.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            executeJob(
                onState = { jobState = it },
                onFailure = { failure ->
                    error = "Conversion failed: ${failure.message ?: failure.javaClass.simpleName}"
                },
                onFinished = {
                    progress = ""
                    busy = false
                },
            ) {
                val r = withContext(Dispatchers.IO) {
                    Converter.run(app, srcs, inputs.target, inputs.options) { i, n ->
                        progress = if (n > 1) "Converting ${i + 1} of $n…" else "Converting…"
                    }
                }
                results = r.outputs
                error = r.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                when {
                    r.errors.isEmpty() -> JobState.SUCCEEDED
                    r.outputs.isNotEmpty() -> JobState.PARTIAL_FAILURE
                    else -> JobState.FAILED
                }
            }
        }
    }
}
