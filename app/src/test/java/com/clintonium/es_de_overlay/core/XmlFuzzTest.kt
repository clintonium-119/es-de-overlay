package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Timeout

/** The XML parser every theme file, gamelist and StrategyWiki answer goes through. */
class XmlFuzzTest {
    /** Any text parses or gives null without throwing, and a DOCTYPE is always refused. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun xml(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        val doc = xmlDocument(text)
        if (text.contains("<!DOCTYPE", ignoreCase = true)) assertNull(doc)
    }
}
