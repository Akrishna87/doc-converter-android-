package com.akrishna.docconverter

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test fun stripsHeadingsAndEmphasis() {
        assertEquals("Title\nHello bold and italic", markdownToPlainText("# Title\nHello **bold** and *italic*"))
    }

    @Test fun convertsListsAndLinks() {
        assertEquals("• one\nsite (http://x.io)", markdownToPlainText("- one\n[site](http://x.io)"))
    }

    @Test fun dropsCodeFences() {
        assertEquals("a = 1", markdownToPlainText("```kotlin\na = 1\n```"))
    }

    @Test fun detectsFormats() {
        assertEquals(Fmt.JPG, detectFormat(null, "photo.JPEG"))
        assertEquals(Fmt.PDF, detectFormat("application/pdf", null))
        assertEquals(Fmt.TXT, detectFormat("text/plain", "notes"))
    }
}

class FeatureTest {
    @Test fun docxRoundTrip() {
        val lines = listOf(
            MdLine("Title", 1),
            MdLine("Body with <tags> & \"quotes\"", 0),
            MdLine("", 0),
            MdLine("Sub", 2),
            MdLine("a\tb", 0),
        )
        assertEquals(lines, readDocx(writeDocx(lines)))
    }

    @Test fun docxReadsBulletsAndStyles() {
        val xml = """<w:document><w:body>
            <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Head</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr><w:tabs><w:tab w:val="left"/></w:tabs></w:pPr><w:r><w:t xml:space="preserve">item</w:t></w:r></w:p>
            <w:p/></w:body></w:document>"""
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use {
            it.putNextEntry(java.util.zip.ZipEntry("word/document.xml")); it.write(xml.toByteArray()); it.closeEntry()
        }
        assertEquals(listOf(MdLine("Head", 2), MdLine("• item", 0), MdLine("", 0)), readDocx(bos.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class) fun docxRejectsGarbage() {
        readDocx("not a zip".toByteArray())
    }

    @Test fun pageRanges() {
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), parsePageRange("1-3, 5,8-", 10))
        assertEquals((0 until 4).toList(), parsePageRange("", 4))
        assertEquals(listOf(0, 1), parsePageRange("-2", 4))
    }

    @Test fun badPageRanges() {
        for (spec in listOf("0", "5-2", "11", "abc", "-", "1-99")) {
            try { parsePageRange(spec, 10); throw AssertionError("accepted $spec") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun commonTargetsForBatches() {
        assertEquals(listOf(Fmt.PDF, Fmt.MD, Fmt.DOCX), commonTargets(listOf(Fmt.TXT)))
        assertEquals(listOf(Fmt.PDF), commonTargets(listOf(Fmt.TXT, Fmt.DOCX, Fmt.JPG)))
        assertEquals(emptyList<Fmt>(), commonTargets(emptyList()))
    }

    @Test fun markdownOutput() {
        assertEquals("# T\n- x\nplain", linesToMarkdown(listOf(MdLine("T", 1), MdLine("• x", 0), MdLine("plain", 0))))
    }
}
