package com.akrishna.docconverter

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.io.ByteArrayOutputStream

data class Output(val uri: Uri, val name: String, val mime: String)

data class Src(val uri: Uri, val name: String, val fmt: Fmt) {
    val base: String get() = name.substringBeforeLast('.').ifBlank { "converted" }
}

data class RunResult(val outputs: List<Output>, val errors: List<String>)

object Converter {
    private const val MAX_IMAGE_SIDE = 4096
    private const val MARGIN = 40

    fun displayName(cr: ContentResolver, uri: Uri): String =
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"

    /** Convert every source to [dst]; failures are collected per file instead of aborting the batch. */
    fun run(ctx: Context, srcs: List<Src>, dst: Fmt, opts: Options, onProgress: (Int, Int) -> Unit): RunResult {
        val outs = mutableListOf<Output>()
        val errs = mutableListOf<String>()
        if (opts.combine && dst == Fmt.PDF && srcs.size > 1) {
            onProgress(0, 1)
            runCatching { outs += combine(ctx, srcs, opts) }.onFailure { errs += "Combine: ${msg(it)}" }
            return RunResult(outs, errs)
        }
        srcs.forEachIndexed { i, s ->
            onProgress(i, srcs.size)
            runCatching { outs += convert(ctx, s, dst, opts) }.onFailure { errs += "${s.name}: ${msg(it)}" }
        }
        return RunResult(outs, errs)
    }

    private fun msg(t: Throwable) = if (t is OutOfMemoryError) "File too large for this device" else t.message ?: t.javaClass.simpleName

    private fun combine(ctx: Context, srcs: List<Src>, opts: Options): Output {
        val parts = srcs.map { s ->
            if (s.fmt == Fmt.PDF) readBytes(ctx.contentResolver, s.uri) else toPdfBytes(ctx, s, opts)
        }
        return save(ctx, "merged.pdf", Fmt.PDF.mime, PdfOps.merge(ctx, parts))
    }

    fun convert(ctx: Context, s: Src, dst: Fmt, opts: Options): List<Output> {
        val cr = ctx.contentResolver
        return when {
            s.fmt.isImage && dst.isImage ->
                listOf(save(ctx, "${s.base}.${dst.ext}", dst.mime, encode(decodeImage(cr, s.uri), dst, opts)))
            s.fmt == Fmt.PDF && dst == Fmt.PDF ->
                PdfOps.extract(ctx, s.uri, opts.pageRange, opts.splitEach, s.base)
                    .map { (name, bytes) -> save(ctx, name, Fmt.PDF.mime, bytes) }
            dst == Fmt.PDF ->
                listOf(save(ctx, "${s.base}.pdf", Fmt.PDF.mime, toPdfBytes(ctx, s, opts)))
            s.fmt == Fmt.PDF && dst.isImage -> pdfToImages(ctx, s, dst, opts)
            else -> {
                val lines = loadLines(ctx, s)
                listOf(save(ctx, "${s.base}.${dst.ext}", dst.mime, linesTo(dst, lines)))
            }
        }
    }

    // ---- reading ----

    private fun readBytes(cr: ContentResolver, uri: Uri): ByteArray =
        cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open file")

    private fun readText(cr: ContentResolver, uri: Uri): String = readBytes(cr, uri).toString(Charsets.UTF_8)

    private fun loadLines(ctx: Context, s: Src): List<MdLine> {
        val cr = ctx.contentResolver
        fun plain(t: String) = t.replace("\r\n", "\n").split('\n').map { MdLine(it, 0) }
        return when (s.fmt) {
            Fmt.TXT -> plain(readText(cr, s.uri))
            Fmt.MD -> parseMarkdown(readText(cr, s.uri))
            Fmt.DOCX -> readDocx(readBytes(cr, s.uri))
            Fmt.PDF -> plain(PdfOps.extractText(ctx, s.uri))
            else -> error("${s.fmt.label} has no text content")
        }
    }

    private fun linesTo(dst: Fmt, lines: List<MdLine>): ByteArray = when (dst) {
        Fmt.TXT -> lines.joinToString("\n") { it.text }.toByteArray()
        Fmt.MD -> linesToMarkdown(lines).toByteArray()
        Fmt.DOCX -> writeDocx(lines)
        else -> error("${dst.label} is not a text format")
    }

    private fun decodeImage(cr: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not decode image")
    }

    // ---- encoding ----

    /** Draw on white so transparent PNGs don't turn black when saved as JPG. */
    private fun flatten(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }

    @Suppress("DEPRECATION")
    private fun encode(bmp: Bitmap, fmt: Fmt, opts: Options): ByteArray {
        val out = ByteArrayOutputStream()
        val q = opts.imageQuality.coerceIn(1, 100)
        when (fmt) {
            Fmt.JPG -> flatten(bmp).compress(Bitmap.CompressFormat.JPEG, q, out)
            Fmt.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            Fmt.WEBP -> bmp.compress(Bitmap.CompressFormat.WEBP, q, out)
            else -> error("Not an image format")
        }
        return out.toByteArray()
    }

    /** Render an image, text, Markdown or DOCX source to PDF bytes. */
    private fun toPdfBytes(ctx: Context, s: Src, opts: Options): ByteArray = when {
        s.fmt.isImage -> imageToPdf(decodeImage(ctx.contentResolver, s.uri), opts)
        s.fmt.isTextLike -> textToPdf(loadLines(ctx, s), opts)
        else -> error("Cannot convert ${s.fmt.label} to PDF")
    }

    private fun imageToPdf(bmp: Bitmap, opts: Options): ByteArray {
        val w = opts.pageSize.w
        val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, w, h), null)
        doc.finishPage(page)
        return doc.toBytes()
    }

    private fun textToPdf(lines: List<MdLine>, opts: Options): ByteArray {
        val pageW = opts.pageSize.w
        val pageH = opts.pageSize.h
        val paint = TextPaint().apply { color = Color.BLACK; textSize = opts.fontSize; isAntiAlias = true }
        val sb = SpannableStringBuilder()
        lines.forEach { l ->
            val start = sb.length
            sb.append(l.text).append('\n')
            if (l.level > 0) {
                val end = sb.length - 1
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val scale = when (l.level) { 1 -> 1.7f; 2 -> 1.4f; 3 -> 1.2f; else -> 1.1f }
                sb.setSpan(RelativeSizeSpan(scale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val width = pageW - 2 * MARGIN
        val usable = pageH - 2 * MARGIN
        val layout = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()

        val doc = PdfDocument()
        var line = 0
        var pageNo = 1
        do {
            val top = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= usable) end++
            if (end == line) end = line + 1 // a single line taller than the page: still advance
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo++).create())
            val c = page.canvas
            c.save()
            c.clipRect(MARGIN, MARGIN, pageW - MARGIN, pageH - MARGIN)
            c.translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
            layout.draw(c)
            c.restore()
            doc.finishPage(page)
            line = end
        } while (line < layout.lineCount)
        return doc.toBytes()
    }

    private fun PdfDocument.toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        writeTo(out)
        close()
        return out.toByteArray()
    }

    private fun pdfToImages(ctx: Context, s: Src, dst: Fmt, opts: Options): List<Output> {
        val pfd = ctx.contentResolver.openFileDescriptor(s.uri, "r") ?: error("Cannot open PDF")
        val results = mutableListOf<Output>()
        PdfRenderer(pfd).use { renderer ->
            val pad = renderer.pageCount.toString().length
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = minOf(opts.pdfScale, MAX_IMAGE_SIDE.toFloat() / maxOf(page.width, page.height))
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val name = if (renderer.pageCount == 1) "${s.base}.${dst.ext}"
                    else "${s.base}_page${(i + 1).toString().padStart(pad, '0')}.${dst.ext}"
                    results += save(ctx, name, dst.mime, encode(bmp, dst, opts))
                    bmp.recycle()
                }
            }
        }
        return results
    }

    // ---- saving ----

    private fun save(ctx: Context, name: String, mime: String, data: ByteArray): Output {
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocConverter")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create $name")
        try {
            cr.openOutputStream(uri)?.use { it.write(data) } ?: error("Could not write $name")
            cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            throw e
        }
        return Output(uri, name, mime)
    }
}
