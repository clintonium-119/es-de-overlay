package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout

/** ES-DE's log and settings, read from shared storage, so any text. */
class EsLogFuzzTest {
    /** Any lines fold into a launch without throwing, and the package it carries is always a package name. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun logLines(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        reportsScreensaver(text)
        var launch: Launch? = null
        for (line in text.lines()) {
            launch = nextLaunch(launch, line)
            screensaverEvent(line)
        }
        launch?.pkg?.let { assertTrue(PACKAGE_NAME.matches(it), it) }
        launch?.let { romPathHint(it, listOf("/storage/7BEF-E760/ROMs", "7BEF-E760:ROMs")) }
    }
}
