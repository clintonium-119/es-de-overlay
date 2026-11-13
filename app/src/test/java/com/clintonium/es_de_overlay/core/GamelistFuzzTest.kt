package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import java.io.IOException
import org.junit.jupiter.api.Timeout

/** A gamelist.xml, which scrapers and other tools write too. */
class GamelistFuzzTest {
    /** Any file reads as games or throws the IOException for one that isn't well-formed or has a DOCTYPE. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun gamelist(input: ByteArray) {
        try {
            gamelistGames(input.inputStream())
            gamelistPaths(input.inputStream(), "Easyland")
            gamelistInfo(input.inputStream(), "Easyland.zip")?.released?.let { isoDate(it) }
        } catch (_: IOException) {
        }
        isoDate(String(input, Charsets.UTF_8))
    }
}
