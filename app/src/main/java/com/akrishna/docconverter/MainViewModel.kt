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
    var source by mutableStateOf<Uri?>(null); private set
    var name by mutableStateOf(""); private set
    var srcFmt by mutableStateOf<Fmt?>(null); private set
    var target by mutableStateOf<Fmt?>(null); private set
    var busy by mutableStateOf(false); private set
    var results by mutableStateOf<List<Output>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set

    fun pick(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        val n = Converter.displayName(cr, uri)
        val fmt = detectFormat(cr.getType(uri), n)
        source = uri
        name = n
        srcFmt = fmt
        target = fmt?.let { targetsFor(it).firstOrNull() }
        results = emptyList()
        error = if (fmt == null) "Unsupported file type" else null
    }

    fun selectTarget(f: Fmt) { target = f }

    fun convert() {
        val uri = source ?: return
        val from = srcFmt ?: return
        val to = target ?: return
        busy = true
        error = null
        results = emptyList()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Converter.convert(getApplication(), uri, from, to, name)
                }
            }.onSuccess { results = it }
                .onFailure { error = it.message ?: "Conversion failed" }
            busy = false
        }
    }
}
