package com.akrishna.docconverter

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val targets: List<Fmt> get() = commonTargets(sources.map { it.fmt })
    val allPdf: Boolean get() = sources.isNotEmpty() && sources.all { it.fmt == Fmt.PDF }
    val anyTextLike: Boolean get() = sources.any { it.fmt.isTextLike }
    val anyPdf: Boolean get() = sources.any { it.fmt == Fmt.PDF }

    /** Add files to the batch ([append] = true) or start a new batch. */
    fun setFiles(uris: List<Uri>, append: Boolean) {
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
        error = if (skipped.isEmpty()) null else "Unsupported file type: ${skipped.joinToString()}"
        if (target !in targets) target = targets.firstOrNull()
    }

    fun remove(src: Src) {
        sources = sources - src
        results = emptyList()
        if (target !in targets) target = targets.firstOrNull()
    }

    fun clear() {
        sources = emptyList(); target = null; results = emptyList(); error = null
    }

    fun selectTarget(f: Fmt) { target = f }

    fun update(f: (Options) -> Options) { options = f(options) }

    fun convert() {
        val to = target ?: return
        val srcs = sources
        if (srcs.isEmpty()) return
        busy = true
        error = null
        results = emptyList()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                Converter.run(getApplication(), srcs, to, options) { i, n ->
                    progress = if (n > 1) "Converting ${i + 1} of $n…" else "Converting…"
                }
            }
            results = r.outputs
            error = r.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            progress = ""
            busy = false
        }
    }
}
