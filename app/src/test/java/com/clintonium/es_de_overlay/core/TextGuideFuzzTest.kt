package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Timeout

/** Text guides, and guides saved as one text section, split at their headings. */
class TextGuideFuzzTest {
    /** Any text splits without throwing, and its sections together hold every line of it, once. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun textSections(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        val sections = textSections("Guide", text)
        val joined = sections.joinToString("") { unescapeHtml(it.html.removePrefix("<pre>").removeSuffix("</pre>")) }
        assertEquals(if (sections.size == 1) text else text.lines().joinToString("\n"), joined)
        isOneTextSection(text)
        splitTextGuide(text)
    }
}
