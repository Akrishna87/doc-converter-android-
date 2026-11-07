package com.akrishna.docconverter

enum class Fmt(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp"),
    PDF("PDF", "pdf", "application/pdf"),
    TXT("Text", "txt", "text/plain"),
    MD("Markdown", "md", "text/markdown");

    val isImage: Boolean get() = this == JPG || this == PNG || this == WEBP
}

/** Guess the format from a MIME type and/or file name. */
fun detectFormat(mime: String?, name: String?): Fmt? {
    when (mime?.lowercase()) {
        "image/jpeg", "image/jpg" -> return Fmt.JPG
        "image/png" -> return Fmt.PNG
        "image/webp" -> return Fmt.WEBP
        "application/pdf" -> return Fmt.PDF
        "text/markdown", "text/x-markdown" -> return Fmt.MD
    }
    val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (ext) {
        "jpg", "jpeg" -> Fmt.JPG
        "png" -> Fmt.PNG
        "webp" -> Fmt.WEBP
        "pdf" -> Fmt.PDF
        "md", "markdown" -> Fmt.MD
        "txt" -> Fmt.TXT
        else -> if (mime?.startsWith("text/") == true) Fmt.TXT else null
    }
}

/** Conversions supported from [src]. */
fun targetsFor(src: Fmt): List<Fmt> = when (src) {
    Fmt.JPG, Fmt.PNG, Fmt.WEBP -> listOf(Fmt.JPG, Fmt.PNG, Fmt.WEBP, Fmt.PDF).filter { it != src }
    Fmt.PDF -> listOf(Fmt.PNG, Fmt.JPG, Fmt.WEBP)
    Fmt.TXT -> listOf(Fmt.PDF, Fmt.MD)
    Fmt.MD -> listOf(Fmt.PDF, Fmt.TXT)
}
