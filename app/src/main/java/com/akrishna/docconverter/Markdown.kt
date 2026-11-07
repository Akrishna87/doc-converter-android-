package com.akrishna.docconverter

private val heading = Regex("^(#{1,6})\\s+(.*)$")
private val bullet = Regex("^(\\s*)[-*+]\\s+")
private val link = Regex("!?\\[([^\\]]*)]\\(([^)]*)\\)")

/** One parsed line of Markdown: its plain text and heading level (0 = body). */
data class MdLine(val text: String, val level: Int)

/** Light-weight Markdown to plain-text lines. Not a full parser, but handles the common syntax. */
fun parseMarkdown(md: String): List<MdLine> {
    var inFence = false
    return md.replace("\r\n", "\n").split('\n').mapNotNull { raw ->
        if (raw.trimStart().startsWith("```")) {
            inFence = !inFence
            return@mapNotNull null
        }
        if (inFence) return@mapNotNull MdLine(raw, 0)

        val h = heading.matchEntire(raw)
        val level = h?.groupValues?.get(1)?.length ?: 0
        var t = h?.groupValues?.get(2) ?: raw
        t = t.replace(bullet) { "${it.groupValues[1]}• " }
        t = t.replace(link) { m ->
            val label = m.groupValues[1].ifBlank { m.groupValues[2] }
            if (m.value.startsWith("!")) label else "$label (${m.groupValues[2]})"
        }
        t = t.replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
        t = t.replace(Regex("(?<![\\w*])\\*(\\S[^*]*?)\\*(?![\\w*])"), "$1")
        MdLine(t, level)
    }
}

fun markdownToPlainText(md: String): String = parseMarkdown(md).joinToString("\n") { it.text }

fun linesToMarkdown(lines: List<MdLine>): String = lines.joinToString("\n") { l ->
    when {
        l.level > 0 -> "#".repeat(l.level.coerceAtMost(6)) + " " + l.text
        l.text.trimStart().startsWith("• ") -> l.text.replaceFirst("• ", "- ")
        else -> l.text
    }
}
