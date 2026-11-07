package com.akrishna.docconverter

enum class PageSize(val label: String, val w: Int, val h: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
}

/** User-adjustable conversion settings. */
data class Options(
    val imageQuality: Int = 92,       // JPG / WEBP quality, 50..100
    val pdfScale: Float = 2f,         // PDF page render scale for image output
    val pageSize: PageSize = PageSize.A4,
    val fontSize: Float = 11f,        // text/Markdown/DOCX to PDF
    val pageRange: String = "",       // PDF to PDF: e.g. "1-3,5,8-"
    val splitEach: Boolean = false,   // PDF to PDF: one file per page
    val combine: Boolean = false,     // batch to PDF: merge into one file
)
