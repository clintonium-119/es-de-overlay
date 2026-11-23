package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelpIconTest {
    /** ES-DE 3.5.0's button_a_switch.svg. */
    private val buttonA = """
        <?xml version="1.0" encoding="UTF-8"?>
        <svg width="64" height="64" version="1.1" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
         <circle id="outline" cx="32" cy="32" r="28" fill="none" stroke="#fff" stroke-width="2"/>
         <path id="button_a" d="m32 4a28 28 0 0 0-28 28 28 28 0 0 0 28 28 28 28 0 0 0 28-28 28 28 0 0 0-28-28zm-4.238281 13.664062h8.457031l7.109375 28.671876h-6.875l-1.582031-6.992188h-5.761719l-1.582031 6.992188h-6.855469l7.089844-28.671876zm4.238281 8.75c-0.208333 1.002605-0.761719 3.509115-1.660156 7.519532h3.359375l-1.699219-7.519532z" fill="#fff"/>
        </svg>
    """.trimIndent()

    @Test
    fun `ES-DE's A icon is a stroked outline circle and a filled letter`() {
        val icon = helpIcon(buttonA)!!
        assertEquals(64f, icon.size)
        assertEquals(2, icon.shapes.size)
        assertEquals(IconShape("M4.0 32.0a28.0 28.0 0 1 0 56.0 0a28.0 28.0 0 1 0 -56.0 0z", 2f, 1f), icon.shapes[0])
        assertEquals(null, icon.shapes[1].stroke)
        assert(icon.shapes[1].pathData.startsWith("m32 4a28 28"))
    }

    @Test
    fun `a D-pad icon's path is filled from its style, across lines`() {
        val dpad = """
            <svg
               width="64"
               viewBox="0 0 64 64">
              <defs id="defs15" />
              <path
                 id="outline_up"
                 style="stroke:none;fill:#ffffff;fill-opacity:1"
                 d="M 26 2 L 38 2 z M 13 26 L 15 37 z " />
            </svg>
        """.trimIndent()
        assertEquals(HelpIcon(64f, listOf(IconShape("M 26 2 L 38 2 z M 13 26 L 15 37 z", null, 1f))), helpIcon(dpad))
        assertNull(helpIcon("""<svg viewBox="0 0 64 64"><rect width="1" height="1"/></svg>"""))
    }

    @Test
    fun `icon names follow the controller type and swap`() {
        assertEquals("button_a_switch", helpIconName("a", false, "switchpro"))
        assertEquals("button_b_switch", helpIconName("a", true, "switchpro"))
        assertEquals("button_y_XBOX", helpIconName("y", false, "xbox"))
        assertEquals("button_x_XBOX", helpIconName("y", true, "xbox360"))
        assertEquals("button_b_PS", helpIconName("b", false, "ps5"))
        assertEquals("button_a_SNES", helpIconName("a", false, "snes"))
        assertEquals("dpad_leftright", helpIconName("left/right", true, "xbox"))
        assertEquals("dpad_updown", helpIconName("up/down", false, "xbox"))
        assertNull(helpIconName("start", false, "xbox"))
    }
}
