package com.akrishna.docconverter

import org.junit.Assert.*
import org.junit.Test

class ConversionInputsTest {
    @Test fun sourceChangesDoNotAlterCapturedBatch() {
        val selected = mutableListOf("first", "second", "third")
        val job = ConversionInputs(selected, "PDF", Options())
        selected.clear()
        selected.add("replacement")
        assertEquals(listOf("first", "second", "third"), job.sources)
    }

    @Test fun preservesOrderAndTarget() {
        val job = ConversionInputs(listOf("z", "a", "m"), "PDF", Options())
        assertEquals(listOf("z", "a", "m"), job.sources)
        assertEquals("PDF", job.target)
    }

    @Test fun capturesEveryOptionBeforeSelectionChanges() {
        var selected = Options(77, 3f, PageSize.LETTER, 15f, "2-4", true, true)
        val expected = selected.copy()
        val job = ConversionInputs(listOf("input"), "PDF", selected)
        selected = Options()
        assertNotEquals(expected, selected)
        assertEquals(expected, job.options)
        assertNotSame(expected, job.options)
    }

    @Test fun editingReturnedListCannotAlterCapturedBatch() {
        val job = ConversionInputs(listOf("first", "second"), "PDF", Options())
        val edited = job.sources.toMutableList()
        edited.reverse()
        edited.add("extra")
        assertEquals(listOf("first", "second"), job.sources)
    }
}
