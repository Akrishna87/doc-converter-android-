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
