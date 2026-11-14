package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import java.io.IOException
import java.io.StringWriter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Timeout

/** Saved guides: pages from the web as the guide builders hand them over, and saved guide files. */
class HtmlGuideFuzzTest {
    /** Any page cleans without throwing, and nothing that runs or loads by itself is left as a tag. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun safeHtml(input: ByteArray) {
        val html = String(input, Charsets.UTF_8)
        val clean = safeHtml(html)
        assertFalse(ACTIVE.containsMatchIn(clean), clean)
        StringWriter().also { writeHtmlGuide(it, html.take(40), listOf(GuideSection("One", html)), emptyMap(), "") }
        unescapeHtml(html)
    }

    /** A saved guide's picture reads, or throws the IOException for one that's missing or cut off. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun guidePicture(input: ByteArray) {
        for (n in 0..2) {
            try {
                guidePicture(input.inputStream(), n)
            } catch (_: IOException) {
            }
        }
    }

    private companion object {
        /** A tag whose whole name is one of these: `<svg->` is another element, which a browser doesn't treat as SVG. */
        val ACTIVE = Regex("<\\s*/?\\s*(script|style|iframe|object|embed|frame|svg|math|base|meta|link|form)(?![A-Za-z0-9:._-])", RegexOption.IGNORE_CASE)
    }
}
