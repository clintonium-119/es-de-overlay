package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaveWatchTest {
    private val esde = "org.es_de.frontend"
    private val retroarch = "com.retroarch.aarch64"
    private val watch = LeaveWatch(esde)

    @Test
    fun `ES-DE in front before the game ever was ends nothing`() {
        assertNull(watch.left(esde, retroarch))
        assertNull(watch.left(esde, retroarch))
    }

    @Test
    fun `the game then ES-DE ends the game once`() {
        assertNull(watch.left(retroarch, retroarch))
        assertEquals(retroarch, watch.left(esde, retroarch))
        assertNull(watch.left(esde, retroarch))
    }

    @Test
    fun `another app between the game and ES-DE still ends the game`() {
        assertNull(watch.left(retroarch, retroarch))
        assertNull(watch.left("com.rp.gameassistant", retroarch))
        assertEquals(retroarch, watch.left(esde, retroarch))
    }

    @Test
    fun `a new launch not yet in front ends nothing`() {
        assertNull(watch.left(retroarch, retroarch))
        assertNull(watch.left(retroarch, "dev.eden.eden_emulator"))
        assertNull(watch.left(esde, "dev.eden.eden_emulator"))
    }

    @Test
    fun `a launch forgotten after the game was in front still ends the game`() {
        assertNull(watch.left("dev.eden.eden_emulator", "dev.eden.eden_emulator"))
        assertEquals("dev.eden.eden_emulator", watch.left(esde, null))
    }

    @Test
    fun `a game ended another way ends nothing`() {
        assertNull(watch.left(retroarch, retroarch))
        watch.forget()
        assertNull(watch.left(esde, null))
    }

    @Test
    fun `no launch ends nothing`() {
        assertNull(watch.left(retroarch, null))
        assertNull(watch.left(esde, null))
        assertNull(watch.left(null, null))
    }
}
