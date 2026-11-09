package com.clintonium.es_de_overlay.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.InputSource

/**
 * [xml] parsed, or null when it isn't well-formed or declares a DOCTYPE. Themes, gamelists and StrategyWiki's answers
 * all come from elsewhere, and refusing the DOCTYPE keeps out entity expansion and external entities on every parser,
 * including Android's, whose DocumentBuilderFactory rejects the features that would switch them off.
 */
fun xmlDocument(xml: String): Document? =
    if (xml.contains("<!DOCTYPE", ignoreCase = true)) null
    else runCatching {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml.removePrefix("\uFEFF"))))
    }.getOrNull()
