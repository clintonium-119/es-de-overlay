package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout

/** ES-DE's log and settings, read from shared storage, so any text. */
class EsLogFuzzTest {
    /** Any lines fold into a launch and a switcher pick without throwing, and the package it carries is always a package name. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun logLines(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        reportsScreensaver(text)
        var launch: Launch? = null
        var pick: Pick? = Pick(0)
        for (line in text.lines()) {
            launch = nextLaunch(launch, line)
            screensaverEvent(line)
            pick = pick?.let { followPick(it, line, 0).first }
        }
        pick?.let { p -> retryPick(p, p.launchedAt + 1_000, { setOf("com.retroarch.aarch64") }) { retroarchProcesses(text) } }
        launch?.pkg?.let { assertTrue(PACKAGE_NAME.matches(it), it) }
        launch?.let { romPathHint(it, listOf("/storage/7BEF-E760/ROMs", "7BEF-E760:ROMs")) }
    }
}
