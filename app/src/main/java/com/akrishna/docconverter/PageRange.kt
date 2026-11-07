package com.akrishna.docconverter

private val rangeRe = Regex("(\\d*)\\s*-\\s*(\\d*)")

/**
 * Parse a page spec such as "1-3,5,8-" into distinct zero-based page indexes.
 * A blank spec selects every page.
 */
fun parsePageRange(spec: String, pageCount: Int): List<Int> {
    fun bad(): Nothing = throw IllegalArgumentException("Use page numbers like 1-3,5,8- (1 to $pageCount)")
    if (pageCount <= 0) throw IllegalArgumentException("The PDF has no pages")
    if (spec.isBlank()) return (0 until pageCount).toList()

    val out = LinkedHashSet<Int>()
    for (raw in spec.split(',')) {
        val part = raw.trim()
        if (part.isEmpty()) continue
        val m = rangeRe.matchEntire(part)
        val from: Int
        val to: Int
        if (m != null) {
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            if (a.isEmpty() && b.isEmpty()) bad()
            from = if (a.isEmpty()) 1 else a.toIntOrNull() ?: bad()
            to = if (b.isEmpty()) pageCount else b.toIntOrNull() ?: bad()
        } else {
            val n = part.toIntOrNull() ?: bad()
            from = n
            to = n
        }
        if (from < 1 || to > pageCount || from > to) bad()
        for (i in from..to) out.add(i - 1)
    }
    if (out.isEmpty()) bad()
    return out.toList()
}
