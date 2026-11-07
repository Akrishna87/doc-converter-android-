package com.akrishna.docconverter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Minimal, dependency-free .docx support: paragraphs, headings (1-3) and bullets.

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private val illegalXml = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")

private fun esc(s: String) =
    s.replace(illegalXml, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun headingStyle(id: Int, name: String, size: Int) =
    "<w:style w:type=\"paragraph\" w:styleId=\"Heading$id\"><w:name w:val=\"$name\"/>" +
        "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"${id - 1}\"/></w:pPr>" +
        "<w:rPr><w:b/><w:sz w:val=\"$size\"/></w:rPr></w:style>"

fun writeDocx(lines: List<MdLine>): ByteArray {
    val body = StringBuilder()
    for (l in lines) {
        body.append("<w:p>")
        val lv = l.level.coerceIn(0, 3)
        if (lv > 0) body.append("<w:pPr><w:pStyle w:val=\"Heading$lv\"/></w:pPr>")
        l.text.split('\t').forEachIndexed { i, part ->
            if (i > 0) body.append("<w:r><w:tab/></w:r>")
            if (part.isNotEmpty()) body.append("<w:r><w:t xml:space=\"preserve\">${esc(part)}</w:t></w:r>")
        }
        body.append("</w:p>")
    }
    val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:document xmlns:w=\"$W_NS\"><w:body>$body" +
        "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
        "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>" +
        "</w:sectPr></w:body></w:document>"
    val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:styles xmlns:w=\"$W_NS\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        headingStyle(1, "heading 1", 40) + headingStyle(2, "heading 2", 32) + headingStyle(3, "heading 3", 26) +
        "</w:styles>"
    val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "</Types>"
    val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"
    val docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun put(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("[Content_Types].xml", contentTypes)
        put("_rels/.rels", rels)
        put("word/document.xml", document)
        put("word/_rels/document.xml.rels", docRels)
        put("word/styles.xml", styles)
    }
    return out.toByteArray()
}

private val paraRe = Regex("<w:p(?:\\s[^>]*)?(?:/>|>.*?</w:p>)", RegexOption.DOT_MATCHES_ALL)
private val pPrRe = Regex("<w:pPr>.*?</w:pPr>", RegexOption.DOT_MATCHES_ALL)
private val styleRe = Regex("<w:pStyle\\s+w:val=\"([^\"]*)\"")
private val headingRe = Regex("(?i)heading\\s*(\\d)")
private val tokenRe = Regex(
    "<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br\\s*/>|<w:cr\\s*/>",
    RegexOption.DOT_MATCHES_ALL,
)
private val numRe = Regex("&#(x?)([0-9a-fA-F]+);")

private fun unescape(s: String): String =
    s.replace(numRe) { m ->
        val cp = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else m.value
    }.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

fun readDocx(bytes: ByteArray): List<MdLine> {
    var xml: String? = null
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            if (e.name == "word/document.xml") {
                xml = zip.readBytes().toString(Charsets.UTF_8)
                break
            }
        }
    }
    val doc = xml ?: throw IllegalArgumentException("Not a valid .docx file")

    return paraRe.findAll(doc).map { p ->
        val para = p.value
        val pPr = pPrRe.find(para)?.value.orEmpty()
        val style = styleRe.find(pPr)?.groupValues?.get(1).orEmpty()
        val level = when {
            style.equals("Title", ignoreCase = true) -> 1
            else -> headingRe.find(style)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 6) ?: 0
        }
        val bullet = level == 0 && pPr.contains("<w:numPr>")
        val sb = StringBuilder()
        for (t in tokenRe.findAll(pPrRe.replace(para, ""))) {
            when {
                t.value.startsWith("<w:tab") -> sb.append('\t')
                t.value.startsWith("<w:br") || t.value.startsWith("<w:cr") -> sb.append('\n')
                else -> sb.append(unescape(t.groupValues[1]))
            }
        }
        MdLine((if (bullet) "• " else "") + sb, level)
    }.toList()
}
