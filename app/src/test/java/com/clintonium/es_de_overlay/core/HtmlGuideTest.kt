package com.clintonium.es_de_overlay.core

import java.io.StringWriter
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlGuideTest {
    private fun html(
        sections: List<GuideSection>,
        images: Map<String, GuideImage> = emptyMap(),
        credit: String = "<a href=\"https://strategywiki.org/wiki/Super_Metroid\">StrategyWiki</a>, CC BY-SA",
    ) = StringWriter().also { writeHtmlGuide(it, "Super Metroid", sections, images, credit) }.toString()

    @Test
    fun `contents links and section ids match in input order`() {
        val out = html(listOf("Controls", "Walkthrough", "Items").map { GuideSection(it, "<p>$it</p>") })
        val contents = Regex("<li><a href=\"#(s\\d+)\">([^<]*)</a></li>").findAll(out).map { it.destructured.toList() }
        val sections = Regex("<section id=\"(s\\d+)\"><h2>([^<]*)</h2>").findAll(out).map { it.destructured.toList() }
        val expected = listOf(listOf("s1", "Controls"), listOf("s2", "Walkthrough"), listOf("s3", "Items"))
        assertEquals(expected, contents.toList())
        assertEquals(expected, sections.toList())
    }

    @Test
    fun `a text section keeps its characters, spaces and line breaks in pre`() {
        val text = "Bosses < 5 & items > 3\n\n   +---+   +---+\n   | A |---| B |\n   +---+   +---+\n\t\"end\"  "
        val out = html(listOf(textSection("FAQ", text)))
        val pre = Regex("<pre>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL).find(out)!!.groupValues[1]
        assertFalse(pre.contains('<'))
        val unescaped = pre.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
        assertEquals(text, unescaped)
    }

    @Test
    fun `a known image key is embedded as a data URI`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 1, 2, -1)
        val out = html(
            listOf(GuideSection("Map", "<p><img alt=\"Crateria\" src=\"img/crateria.png\" width=\"320\"></p>")),
            mapOf("img/crateria.png" to GuideImage("image/png", png)),
        )
        val img = Regex("<img [^>]*>").find(out)!!.value
        val data = Regex("src=\"data:image/png;base64,([^\"]*)\"").find(img)!!.groupValues[1]
        assertArrayEquals(png, Base64.getDecoder().decode(data))
        assertTrue(img.contains("alt=\"Crateria\"") && img.contains("width=\"320\""))
    }

    @Test
    fun `an unknown image key drops src and srcset but keeps alt`() {
        val out = html(
            listOf(GuideSection("Map", "<IMG SRC='https://example.com/x.png' srcset=\"x2.png 2x\" alt=\"Brinstar\">")),
        )
        val img = Regex("<IMG[^>]*>").find(out)!!.value
        assertFalse(img.contains("src", ignoreCase = true))
        assertTrue(img.contains("alt=\"Brinstar\""))
    }

    @Test
    fun `links the stylesheet, ends with the credit and has no script`() {
        val credit = "From <a href=\"https://strategywiki.org/wiki/Super_Metroid\">StrategyWiki</a>, CC BY-SA 4.0"
        val out = html(listOf(GuideSection("Controls", "<p>Run</p>")), credit = credit)
        assertTrue(out.contains("<link rel=\"stylesheet\" href=\"guide.css\">"))
        assertTrue(out.trimEnd().endsWith("<footer>$credit</footer>\n</body></html>"))
        assertFalse(out.contains("<script", ignoreCase = true))
    }

    @Test
    fun `guidePicture reads back each embedded picture in file order`() {
        val a = ByteArray(70_000) { (it * 7).toByte() }
        val b = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val out = html(
            listOf(
                GuideSection("Maps", "<p>Town</p><img src=\"img:a\" alt=\"Town\"><img src=\"img:gone\" alt=\"Unsaved\">"),
                GuideSection("Items", "<p>Lamp &lt;img src=\"data:</p><img alt=\"Lamp\" src=\"img:b\">"),
            ),
            mapOf("img:a" to GuideImage("image/png", a), "img:b" to GuideImage("image/gif", b)),
        )
        assertArrayEquals(a, guidePicture(out.byteInputStream(), 0))
        assertArrayEquals(b, guidePicture(out.byteInputStream(), 1))
        assertTrue(runCatching { guidePicture(out.byteInputStream(), 2) }.exceptionOrNull() is java.io.IOException)
    }
}
