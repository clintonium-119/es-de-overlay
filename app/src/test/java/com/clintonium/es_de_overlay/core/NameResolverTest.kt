package com.clintonium.es_de_overlay.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameResolverTest {
    private fun shot(rel: String, system: String = "snes", dir: Boolean = false) =
        mediaPath(GameRef(system, rel), "screenshots", "png", dir).joinToString("/")

    @Test
    fun `subfolder ROM mirrors into the media path`() {
        assertEquals(
            "snes/screenshots/01) All but the Best/Super Metroid.png",
            shot("01) All but the Best/Super Metroid.sfc"),
        )
    }

    @Test
    fun `directory-as-file keeps its pseudo-extension and nested folders mirror`() {
        assertEquals("scummvm/screenshots/dig.scummvm.png", shot("dig.scummvm", "scummvm", dir = true))
        assertEquals(
            "c64/screenshots/Multidisk/Last Ninja 2/Last Ninja 2.png",
            shot("Multidisk/Last Ninja 2/Last Ninja 2.d64", "c64"),
        )
    }

    @Test
    fun `videos use mp4, extensions are lower-cased and extensionless names stay whole`() {
        assertEquals(
            listOf("snes", "videos", "Super Metroid (Redesign).mp4"),
            mediaPath(GameRef("snes", "Super Metroid (Redesign).SFC"), "videos", "MP4", false),
        )
        assertEquals("snes/screenshots/README.png", shot("README"))
    }

    @Test
    fun `overwrite policies`() {
        val existing = setOf("X.png", "X-1.png")
        assertEquals("X-2.png", targetName("X.png", Overwrite.SUFFIX, existing::contains))
        assertNull(targetName("X.png", Overwrite.SKIP, existing::contains))
        assertEquals("X.png", targetName("X.png", Overwrite.REPLACE, existing::contains))
        assertEquals("Y.png", targetName("Y.png", Overwrite.SKIP, existing::contains))
    }

    // End to end over the device fixtures: launch block -> GameRef -> media path.
    private val roots = listOf("/storage/7BEF-E760/ROMs", "7BEF-E760:ROMs")
    private fun launch(name: String) = File("../fixtures/es_log/$name").readLines().fold(null as Launch?, ::nextLaunch)!!
    private val androidGamelist = "<gameList><game><path>./Dead Cells.app</path><name>Dead Cells</name></game></gameList>"

    @Test
    fun `fixtures resolve to the expected media paths`() {
        val switch = resolveGame(launch("switch_romprovider.txt"), roots) { error("log path should win") }!!
        assertEquals(
            "switch/screenshots/Mario Kart 8 Deluxe.png",
            mediaPath(switch, "screenshots", "png", false).joinToString("/"),
        )
        val android = resolveGame(launch("android_app.txt"), roots) { androidGamelist.byteInputStream() }!!
        assertEquals(
            "androidgames/videos/Dead Cells.mp4",
            mediaPath(android, "videos", "mp4", false).joinToString("/"),
        )
    }

    @Test
    fun `no path and no unique gamelist match resolves to nothing`() {
        assertNull(resolveGame(launch("android_app.txt"), roots) { null })
        val dup = "<gameList><game><path>./a/Dead Cells.app</path><name>Dead Cells</name></game>" +
            "<game><path>./b/Dead Cells.app</path><name>Dead Cells</name></game></gameList>"
        assertNull(resolveGame(launch("android_app.txt"), roots) { dup.byteInputStream() })
    }
}
