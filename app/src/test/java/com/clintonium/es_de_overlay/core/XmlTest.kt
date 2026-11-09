package com.clintonium.es_de_overlay.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTest {
    @Test
    fun `doctype with external entity is rejected`() {
        assertNull(xmlDocument("<?xml version=\"1.0\"?>\n<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hostname\">]>\n<x>&e;</x>"))
        assertNull(xmlDocument("<!doctype x [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]><x>&b;</x>"))
        assertNull(xmlDocument("<x><y></x>"))
    }

    @Test
    fun `plain documents parse`() {
        val doc = xmlDocument("\uFEFF<?xml version=\"1.0\"?>\n<gameList><game><name>A &amp; B</name></game></gameList>")!!
        assertEquals("A & B", doc.getElementsByTagName("name").item(0).textContent)
        val page = File("../fixtures/guides/strategywiki-super-metroid-page.xml").inputStream().use(::parsedPage)!!
        assertEquals("Super Metroid/Crateria", page.first)
        val gamelist = "<?xml version=\"1.0\"?>\n<alternativeEmulator><label>x</label></alternativeEmulator>\n" +
            "<gameList><game><path>./Easyland.zip</path><name>Easyland</name></game></gameList>"
        assertEquals(listOf(GamelistGame("Easyland.zip", "Easyland", null)), gamelistGames(gamelist.byteInputStream()))
    }
}
