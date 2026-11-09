package com.akrishna.docconverter

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF operations backed by PdfBox-Android: merge, extract/split pages, text extraction. */
object PdfOps {
    private var initialised = false

    private fun init(ctx: Context) {
        if (!initialised) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            initialised = true
        }
    }

    private fun open(ctx: Context, uri: Uri): PDDocument {
        init(ctx)
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
        try {
            return input.use { PDDocument.load(it) }
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException("The PDF is password-protected")
        }
    }

    fun extractText(ctx: Context, uri: Uri): String {
        val text = open(ctx, uri).use { PDFTextStripper().getText(it) }
        if (text.isBlank()) throw IllegalStateException("No selectable text found (scanned PDF?)")
        return text
    }

    /** Returns (file name, pdf bytes) pairs: one per page when [splitEach], else a single file. */
    fun extract(ctx: Context, uri: Uri, spec: String, splitEach: Boolean, base: String): List<Pair<String, ByteArray>> {
        if (spec.isBlank() && !splitEach) {
            throw IllegalArgumentException("Enter page numbers (e.g. 1-3,5) or turn on Split every page")
        }
        return open(ctx, uri).use { src ->
            val pages = parsePageRange(spec, src.numberOfPages)
            if (splitEach) pages.map { i -> "${base}_page${i + 1}.pdf" to build(src, listOf(i)) }
            else listOf("${base}_extract.pdf" to build(src, pages))
        }
    }

    private fun build(src: PDDocument, pages: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { dst ->
            pages.forEach { dst.importPage(src.getPage(it)) }
            dst.save(out)
        }
        return out.toByteArray()
    }

    fun mergeFiles(ctx: Context, parts: List<File>, destination: File, scratchDir: File) {
        init(ctx)
        val merger = PDFMergerUtility()
        parts.forEach { merger.addSource(it) }
        merger.destinationFileName = destination.absolutePath
        val memory = MemoryUsageSetting.setupTempFileOnly()
        memory.setTempDir(scratchDir)
        merger.mergeDocuments(memory)
    }

    fun merge(ctx: Context, parts: List<ByteArray>): ByteArray {
        init(ctx)
        val out = ByteArrayOutputStream()
        val merger = PDFMergerUtility()
        parts.forEach { merger.addSource(ByteArrayInputStream(it)) }
        merger.destinationStream = out
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        return out.toByteArray()
    }
}
