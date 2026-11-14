package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Timeout

/** ES-DE's settings and input files, a theme's files, and the tar stream a downloaded theme is copied in. */
class EsThemeFuzzTest {
    /** es_settings.xml and es_input.xml read as settings without throwing. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun settings(input: ByteArray) {
        themeSettings(input.inputStream())
        mediaDirectory(input.inputStream())
        esInputButtons(input.inputStream())
    }

    /**
     * A theme whose theme.xml, capabilities.xml and every other file are three parts of the input, split at NUL, so a
     * file can include itself and others: resolving it ends without throwing.
     */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun theme(input: ByteArray) {
        val parts = String(input, Charsets.UTF_8).split('\u0000')
        fun part(i: Int) = parts.getOrNull(i)
        val read = { path: String ->
            when (path) {
                "theme.xml" -> part(0)
                "capabilities.xml" -> part(1)
                else -> part(2)
            }
        }
        resolveTheme(read, ThemeSettings(), "snes", 4 / 3f, "screenshots") { 0xFF336699.toInt() }
    }

    /** An SVG picture and a theme colour read without throwing. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun svg(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        svgFill(text)
        svgCornerRadius(text)?.let { assertFalse(it.isNaN() || it < 0f || it > 0.5f, "radius $it") }
        themeColor(text)
    }

    /** Any stream unpacks without throwing, and never hands over a path that is absolute or goes up. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun tar(input: ByteArray) {
        unpackFiles(input.inputStream()) { path, _ ->
            assertFalse(path.startsWith("/") || path.split("/").any { it == ".." }, path)
        }
    }
}
