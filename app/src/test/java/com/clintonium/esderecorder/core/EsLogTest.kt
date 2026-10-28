package com.clintonium.esderecorder.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EsLogTest {
    private val roots = listOf("/storage/7BEF-E760/ROMs", "7BEF-E760:ROMs")

    private fun parse(lines: List<String>) = lines.fold(null as Launch?, ::nextLaunch)!!
    private fun fixture(name: String) = parse(File("../fixtures/es_log/$name").readLines())

    @Test
    fun `switch launch with ROMPROVIDER yields system, package and decoded path`() {
        val launch = fixture("switch_romprovider.txt")
        assertEquals("Mario Kart 8 Deluxe", launch.name)
        assertEquals("switch", launch.system)
        assertEquals("dev.eden.eden_emulator", launch.pkg)
        assertEquals("Mario Kart 8 Deluxe.nsp", romPathHint(launch, roots))
    }

    @Test
    fun `android app launch has a package but no path`() {
        val launch = fixture("android_app.txt")
        assertEquals("Dead Cells", launch.name)
        assertEquals("androidgames", launch.system)
        assertEquals("com.netflix.NGP.DeadCellsReturnToCastlevania", launch.pkg)
        assertNull(romPathHint(launch, roots))
    }

    @Test
    fun `last launch wins and earlier block lines are not mixed in`() {
        val launch = fixture("session_tail.txt")
        assertEquals("Stuffy Frights", launch.name)
        assertEquals("Oct 28 15:09:17", launch.stamp)
        assertEquals("com.UnityTechnologies.com.unity.template.urpblank.phone", launch.pkg)
        assertEquals(4, launch.lines.size)
    }

    @Test
    fun `SAF document uri keeps plus signs and subfolders`() {
        val launch = parse(
            listOf(
                """Oct 28 16:00:00 Info:   Launching game "A+B" from system "Nintendo SNES (Super Nintendo) (snes)"...""",
                "Oct 28 16:00:00 Info:   %ROMSAF% expanded: content://com.android.externalstorage.documents/tree/" +
                    "7BEF-E760%3AROMs%2Fsnes/document/7BEF-E760%3AROMs%2Fsnes%2F01%29%20All%20but%20the%20Best%2FA%2BB.sfc",
            ),
        )
        assertEquals("snes", launch.system)
        assertEquals("01) All but the Best/A+B.sfc", romPathHint(launch, roots))
    }

    @Test
    fun `raw absolute path is not percent-decoded`() {
        val launch = parse(
            listOf(
                """Oct 28 16:00:00 Info:   Launching game "100%AB" from system "Nintendo SNES (snes)"...""",
                "Oct 28 16:00:00 Info:   Extra ROM: /storage/7BEF-E760/ROMs/snes/100%AB.sfc",
            ),
        )
        assertEquals("100%AB.sfc", romPathHint(launch, roots))
    }
}
