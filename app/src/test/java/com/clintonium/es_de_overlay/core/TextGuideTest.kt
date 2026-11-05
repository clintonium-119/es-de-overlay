package com.clintonium.es_de_overlay.core

import java.io.File
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextGuideTest {
    // Excerpts of real guides saved on the Nova: every heading, contents and rule line with two lines around it,
    // other text cut to "(…)", contact details removed.
    private fun text(name: String) = File("../fixtures/guides/text/$name.txt").readText()

    private fun pre(section: GuideSection) = unescapeHtml(section.html.removePrefix("<pre>").removeSuffix("</pre>"))

    /** The sections' names, after checking that their texts, one after another, are the guide's text line for line. */
    private fun names(title: String, text: String): List<String> {
        val sections = textSections(title, text)
        assertEquals(text.lines().joinToString("\n"), sections.joinToString("", transform = ::pre))
        return sections.map { it.title }
    }

    @Test
    fun `GameFAQs contents letters split Shantae at its boxed headings`() {
        val text = text("gamefaqs-shantae")
        assertEquals(
            listOf("Shantae FAQ", "Controls", "Items", "Walkthrough", "Lists", "Thanks", "Experimental"),
            names("Shantae FAQ", text),
        )
        // The box above a heading opens its section, and no section shows a heading of its own.
        val controls = textSections("Shantae FAQ", text)[1]
        assertEquals(listOf("#".repeat(79), " ".repeat(33) + "(B) CONTROLS"), pre(controls).lines().take(2))
        assertFalse(controls.heading)
        // The two blank lines before the next box both stay: a <pre> drops only a final line break.
        assertTrue(pre(controls).endsWith("items.\n\n\n"))
    }

    @Test
    fun `GameFAQs contents numbers split Pitfall, with its decorated rules`() {
        val text = text("gamefaqs-pitfall")
        assertEquals(listOf("Pitfall", "Notes", "Game Information", "Walkthrough", "Tips", "Disclaimer"), names("Pitfall", text))
        assertEquals(listOf("×•".repeat(12), "1. NOTES"), pre(textSections("Pitfall", text)[1]).lines().take(2))
    }

    @Test
    fun `Neoseeker contents codes split Link's Awakening at the coded headings`() {
        val names = names("Link's Awakening", text("neoseeker-links-awakening-dx"))
        assertEquals(
            listOf(
                "Link's Awakening", "Introduction", "The Basics", "The Walkthrough", "Bestiary", "Pieces of Heart",
                "Secret Seashells", "Glitches and Tricks", "References to Other Games", "Frequently Asked Questions",
                "Version History", "Copyright", "Contact Information",
            ),
            names,
        )
    }

    @Test
    fun `without contents, overlined headings split Solaris`() {
        assertEquals(
            listOf(
                "Solaris", "INTRODUCTION", "UPDATES", "STORY", "CONTROLS", "MAIN SCREEN DESCRIPTION", "MAP SCREEN DESCRIPTION",
                "QUADRANTS, SECTORS, AND SPACE", "WARPING", "ALLIES", "ENEMIES", "RED ZONE", "WALKTHROUGH",
                "TIPS, TRICKS, AND SECRETS (some from the manual)", "CREDITS",
            ),
            names("Solaris", text("gamefaqs-solaris")),
        )
    }

    @Test
    fun `boxed and underlined headings split, and body text after a box doesn't`() {
        val text = "Intro\n\n====================\nFirst\n====================\nbody\n\nSecond\n--------------------\nmore"
        assertEquals(listOf("Guide", "First", "Second"), names("Guide", text))
    }

    @Test
    fun `a text with no headings stays one section`() {
        val text = "Just some notes.\n1. Go left\n2. Go right\n"
        assertEquals(listOf(textSection("Notes", text)), textSections("Notes", text))
    }

    @Test
    fun `a guide saved as one text section splits when it opens`() {
        val credit = "From <a href=\"https://gamefaqs.gamespot.com/x\">GameFAQs</a> by DEngel."
        val saved = StringWriter().also {
            writeHtmlGuide(it, "Shantae – Guide", listOf(textSection("Shantae – Guide", text("gamefaqs-shantae"))), emptyMap(), credit)
        }.toString()
        assertTrue(isOneTextSection(saved.take(4096)))
        val split = splitTextGuide(saved)!!
        assertTrue("<section id=\"s7\">" in split && "<section id=\"s8\">" !in split)
        assertTrue("<li><a href=\"#s2\">Controls</a></li>" in split)
        assertFalse("<h2>" in split)
        assertTrue(split.trimEnd().endsWith("<footer>$credit</footer>\n</body></html>"))
    }

    @Test
    fun `other guides aren't split again`() {
        val web = StringWriter().also {
            writeHtmlGuide(it, "Super Metroid", listOf(GuideSection("A", "<p>a</p>"), GuideSection("B", "<p>b</p>")), emptyMap(), "")
        }.toString()
        assertFalse(isOneTextSection(web))
        assertNull(splitTextGuide(web))
        val plain = StringWriter().also { writeHtmlGuide(it, "Notes", listOf(textSection("Notes", "no headings")), emptyMap(), "") }
        assertNull(splitTextGuide(plain.toString()))
    }
}
