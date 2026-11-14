package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout

/** Pages and answers from the guide sites, and the addresses and titles they give. */
class GuideSourcesFuzzTest {
    /** VGMaps' atlas, StrategyWiki's answers and pages read without throwing, but for StrategyWiki's IOException. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun pages(input: ByteArray) {
        val html = String(input, Charsets.UTF_8)
        vgmapsMaps(html, "Shantae")
        val pages = guidePages("Shantae", html)
        wikiSection("Shantae", html, pages)
        try {
            parsedPage(input.inputStream())
        } catch (_: IOException) {
        }
    }

    /** Any address or title gives a YouTube watch page or nothing, and a file name that stays one file. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun names(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        youtubeVideo(text)?.let { assertTrue(it.startsWith("https://www.youtube.com/watch?v="), it) }
        siteOf(text)
        isGuidePage(text)
        onSameSite("https://gamefaqs.gamespot.com/gbc/", text)
        isBotCheck(text)
        cleanName(text)
        matchKey(text)
        guideAuthor(text)
        val file = guideFileName("GameFAQs", text)
        assertTrue(file.length <= 120 && file.endsWith(".html"), file)
        assertFalse('/' in file || '\u0000' in file, file)
    }
}
