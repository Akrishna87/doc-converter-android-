package com.akrishna.docconverter

/** Captures selection metadata, not the contents of externally supplied files. */
internal class ConversionInputs<S, T>(sources: List<S>, val target: T, options: Options) {
    private val capturedSources = sources.toList()
    val sources: List<S> get() = capturedSources.toList()
    val options: Options = options.copy()
}
