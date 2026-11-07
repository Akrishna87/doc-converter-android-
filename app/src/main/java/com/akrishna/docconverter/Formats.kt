package com.akrishna.docconverter

const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

enum class Fmt(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp"),
    PDF("PDF", "pdf", "application/pdf"),
    TXT("Text", "txt", "text/plain"),
    MD("Markdown", "md", "text/markdown"),
    DOCX("Word (DOCX)", "docx", DOCX_MIME);

    val isImage: Boolean get() = this == JPG || this == PNG || this == WEBP
    val isTextLike: Boolean get() = this == TXT || this == MD || this == DOCX
}

/** Guess the format from a MIME type and/or file name. */
fun detectFormat(mime: String?, name: String?): Fmt? {
    when (mime?.lowercase()) {
        "image/jpeg", "image/jpg" -> return Fmt.JPG
        "image/png" -> return Fmt.PNG
        "image/webp" -> return Fmt.WEBP
        "application/pdf" -> return Fmt.PDF
        "text/markdown", "text/x-markdown" -> return Fmt.MD
        DOCX_MIME -> return Fmt.DOCX
    }
    val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (ext) {
        "jpg", "jpeg" -> Fmt.JPG
        "png" -> Fmt.PNG
        "webp" -> Fmt.WEBP
        "pdf" -> Fmt.PDF
        "md", "markdown" -> Fmt.MD
        "txt" -> Fmt.TXT
        "docx" -> Fmt.DOCX
        else -> if (mime?.startsWith("text/") == true) Fmt.TXT else null
    }
}

/** Conversions supported from [src]. PDF to PDF means "extract / split pages". */
fun targetsFor(src: Fmt): List<Fmt> = when (src) {
    Fmt.JPG, Fmt.PNG, Fmt.WEBP -> listOf(Fmt.JPG, Fmt.PNG, Fmt.WEBP, Fmt.PDF).filter { it != src }
    Fmt.PDF -> listOf(Fmt.PNG, Fmt.JPG, Fmt.WEBP, Fmt.TXT, Fmt.MD, Fmt.DOCX, Fmt.PDF)
    Fmt.TXT -> listOf(Fmt.PDF, Fmt.MD, Fmt.DOCX)
    Fmt.MD -> listOf(Fmt.PDF, Fmt.TXT, Fmt.DOCX)
    Fmt.DOCX -> listOf(Fmt.PDF, Fmt.TXT, Fmt.MD)
}

/** Targets that every format in [fmts] can convert to. */
fun commonTargets(fmts: Collection<Fmt>): List<Fmt> {
    val distinct = fmts.distinct()
    if (distinct.isEmpty()) return emptyList()
    return distinct.drop(1).fold(targetsFor(distinct[0])) { acc, f -> acc.filter { it in targetsFor(f) } }
}
