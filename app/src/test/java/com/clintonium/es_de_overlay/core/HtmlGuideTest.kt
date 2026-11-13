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
    fun `a tag written inside an attribute value stays text and can't end the attribute`() {
        val out = html(
            listOf(GuideSection("Map", "<a href=\"<img src='img/k.png' onfocus=alert(1) autofocus>\">Map</a>")),
            mapOf("img/k.png" to GuideImage("image/png", byteArrayOf(1, 2, 3))),
        )
        assertTrue(out.contains("<a href=\"&lt;img src='img/k.png' onfocus=alert(1) autofocus&gt;\">Map</a>"))
        assertFalse(out.contains("data:image"))
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

    private fun section(out: String) = Regex("<section id=\"s1\">(.*)</section>", RegexOption.DOT_MATCHES_ALL).find(out)!!.groupValues[1]

    @Test
    fun `saved page drops scripts handlers and javascript links`() {
        val png = byteArrayOf(1, 2, 3)
        val page = "<p onclick=\"steal()\">Go <b>left</b> at the fork.</p><script>alert(1)</script>" +
            "<a href=\"javascript:alert(2)\">Map</a> <a href=' JaVa&#115;cript:alert(3)'>Again</a> <a href=\"java&Tab;script:x\">Tab</a>" +
            "<img src=\"img:0\" alt=\"Fork\" ONERROR=\"alert(4)\"><a href=\"#s2\">Next</a><img/src=\"x\"/onerror=alert(5)>"
        val out = section(html(listOf(GuideSection("Forest", page)), mapOf("img:0" to GuideImage("image/png", png))))
        assertTrue(out.contains("<p>Go <b>left</b> at the fork.</p>"))
        assertTrue(out.contains("<a>Map</a> <a>Again</a> <a>Tab</a>"))
        assertTrue(out.contains("<img src=\"data:image/png;base64,${Base64.getEncoder().encodeToString(png)}\" alt=\"Fork\">"))
        assertTrue(out.contains("<a href=\"#s2\">Next</a>"))
        for (bad in listOf("<script", "alert", "onclick", "onerror", "javascript", "steal")) assertFalse(bad, out.contains(bad, ignoreCase = true))
    }

    @Test
    fun `a link to a YouTube video is saved as a link`() {
        val link = "<a href=\"https://www.youtube.com/watch?v=GIc8uHGR2dM\">YouTube video</a>"
        assertTrue(section(html(listOf(GuideSection("Donut Plains", "<p>$link</p>")), emptyMap())).contains(link))
    }

    @Test
    fun `saved page drops iframes objects forms and base`() {
        val page = "<base href=\"https://evil.example/\"><meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">" +
            "<iframe src=\"https://evil.example\">frame text</iframe><object data=\"x.swf\"><embed src=\"x.swf\"></object>" +
            "<form action=\"https://evil.example\"><p>Inside a form</p><button formaction=\"javascript:alert(1)\">Go</button></form>" +
            "<svg><a xlink:href=\"javascript:alert(2)\">Svg</a></svg><noembed><img title=\"</noembed><img src onerror=alert(3)>\"></noembed>" +
            "<p>Text</p><!-- <script>alert(4)</script> --><scr<script>ipt>alert(5)</script><a href=\"data:text/html,x\">Data</a>"
        val out = section(html(listOf(GuideSection("Traps", page))))
        assertTrue(out.contains("<p>Inside a form</p><button>Go</button>"))
        assertTrue(out.contains("<a>Svg</a>") && out.contains("<p>Text</p>") && out.contains("<a>Data</a>"))
        val bad = listOf("<base", "<meta", "<iframe", "frame text", "<object", "<embed", "<form", "<svg", "<noembed", "<script", "evil", "alert")
        for (b in bad) assertFalse(b, out.contains(b, ignoreCase = true))
    }

    @Test
    fun `an image type that isn't one is written as png`() {
        val out = html(
            listOf(GuideSection("Map", "<img src=\"img:0\">")),
            mapOf("img:0" to GuideImage("image/png\" onload=\"alert(1)", byteArrayOf(1))),
        )
        assertTrue(out.contains("<img src=\"data:image/png;base64,AQ==\">"))
        assertFalse(out.contains("alert"))
    }

    @Test
    fun `guidePicture reads back each embedded picture in file order, skipping SVG`() {
        val a = ByteArray(70_000) { (it * 7).toByte() }
        val b = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val out = html(
            listOf(
                GuideSection("Maps", "<p>Town</p><img src=\"img:a\" alt=\"Town\"><img src=\"img:gone\" alt=\"Unsaved\">"),
                GuideSection("Items", "<p>Lamp &lt;img src=\"data:</p><img src=\"img:icon\"><img alt=\"Lamp\" src=\"img:b\">"),
            ),
            mapOf(
                "img:a" to GuideImage("image/png", a),
                "img:icon" to GuideImage("image/svg+xml", "<svg/>".toByteArray()),
                "img:b" to GuideImage("image/gif", b),
            ),
        )
        assertArrayEquals(a, guidePicture(out.byteInputStream(), 0))
        assertArrayEquals(b, guidePicture(out.byteInputStream(), 1))
        assertTrue(runCatching { guidePicture(out.byteInputStream(), 2) }.exceptionOrNull() is java.io.IOException)
    }
}
