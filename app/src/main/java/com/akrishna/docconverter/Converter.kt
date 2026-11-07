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

object Converter {
    private const val MAX_IMAGE_SIDE = 4096
    private const val PAGE_W = 595 // A4 @ 72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40

    fun displayName(cr: ContentResolver, uri: Uri): String =
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"

    /** Convert [src] to [dst]; results are saved in Downloads/DocConverter. */
    fun convert(ctx: Context, src: Uri, srcFmt: Fmt, dst: Fmt, srcName: String): List<Output> {
        val cr = ctx.contentResolver
        val base = srcName.substringBeforeLast('.').ifBlank { "converted" }
        return when {
            srcFmt.isImage && dst.isImage -> {
                val bmp = decodeImage(cr, src)
                listOf(save(ctx, "$base.${dst.ext}", dst.mime, encode(bmp, dst)))
            }
            srcFmt.isImage && dst == Fmt.PDF -> {
                val bmp = decodeImage(cr, src)
                listOf(save(ctx, "$base.pdf", dst.mime, imageToPdf(bmp)))
            }
            srcFmt == Fmt.PDF && dst.isImage -> pdfToImages(ctx, src, base, dst)
            (srcFmt == Fmt.TXT || srcFmt == Fmt.MD) && dst == Fmt.PDF -> {
                val text = readText(cr, src)
                val lines = if (srcFmt == Fmt.MD) parseMarkdown(text) else
                    text.replace("\r\n", "\n").split('\n').map { MdLine(it, 0) }
                listOf(save(ctx, "$base.pdf", dst.mime, textToPdf(lines)))
            }
            srcFmt == Fmt.MD && dst == Fmt.TXT ->
                listOf(save(ctx, "$base.txt", dst.mime, markdownToPlainText(readText(cr, src)).toByteArray()))
            srcFmt == Fmt.TXT && dst == Fmt.MD ->
                listOf(save(ctx, "$base.md", dst.mime, readText(cr, src).toByteArray()))
            else -> error("Unsupported conversion: ${srcFmt.label} to ${dst.label}")
        }
    }

    // ---- reading ----

    private fun readText(cr: ContentResolver, uri: Uri): String =
        cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot open file")

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
    private fun encode(bmp: Bitmap, fmt: Fmt): ByteArray {
        val out = ByteArrayOutputStream()
        when (fmt) {
            Fmt.JPG -> flatten(bmp).compress(Bitmap.CompressFormat.JPEG, 92, out)
            Fmt.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            Fmt.WEBP -> bmp.compress(Bitmap.CompressFormat.WEBP, 92, out)
            else -> error("Not an image format")
        }
        return out.toByteArray()
    }

    private fun imageToPdf(bmp: Bitmap): ByteArray {
        val pageH = (PAGE_W.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, pageH, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, PAGE_W, pageH), null)
        doc.finishPage(page)
        return doc.toBytes()
    }

    private fun textToPdf(lines: List<MdLine>): ByteArray {
        val paint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
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
        val width = PAGE_W - 2 * MARGIN
        val usable = PAGE_H - 2 * MARGIN
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
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            val c = page.canvas
            c.save()
            c.clipRect(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN)
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

    private fun pdfToImages(ctx: Context, uri: Uri, base: String, dst: Fmt): List<Output> {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF")
        val results = mutableListOf<Output>()
        PdfRenderer(pfd).use { renderer ->
            val pad = renderer.pageCount.toString().length
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = 2f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val name = if (renderer.pageCount == 1) "$base.${dst.ext}"
                    else "${base}_page${(i + 1).toString().padStart(pad, '0')}.${dst.ext}"
                    results += save(ctx, name, dst.mime, encode(bmp, dst))
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
