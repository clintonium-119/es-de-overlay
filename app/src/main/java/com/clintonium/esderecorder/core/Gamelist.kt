package com.clintonium.esderecorder.core

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** ROM-relative paths (without the leading "./") of the gamelist.xml games whose <name> is [name]. */
fun gamelistPaths(xml: InputStream, name: String): List<String> {
    val games = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).getElementsByTagName("game")
    return (0 until games.length).mapNotNull { i ->
        val game = games.item(i) as Element
        fun text(tag: String) = game.getElementsByTagName(tag).item(0)?.textContent?.trim()
        if (text("name") == name.trim()) text("path")?.removePrefix("./") else null
    }
}
