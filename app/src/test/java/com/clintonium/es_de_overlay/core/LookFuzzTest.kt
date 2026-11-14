package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Timeout

/** An overlay-theme.xml from the ES-DE folder, any text. */
class LookFuzzTest {
    /** Any file reads as overrides without throwing, and its font paths stay below the ES-DE folder. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun overlayTheme(input: ByteArray) {
        val look = look(null, "linear-es-de", "", "fuzz").with(overlayTheme(input.inputStream()))
        for (path in listOfNotNull(look.fontPath, look.bodyFontPath)) {
            assertFalse(path.startsWith("/") || path.split("/").any { it == ".." }, path)
        }
    }
}
