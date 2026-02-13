/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.service

import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Resolves (a|b|c) selection groups and ~factoid references in factoid text values */
@Service
class FactoidTextRenderer(private val factoidService: FactoidService) {
    private val logger = LoggerFactory.getLogger(FactoidTextRenderer::class.java)
    private val selectionPattern = Regex("""\(([^)]+\|[^)]+)\)""")

    /** Replaces all (x|y|z) groups with a randomly chosen element, resolving ~ references */
    fun resolveSelections(value: String, hopsRemaining: Int): String {
        if (hopsRemaining <= 0) return value
        return selectionPattern.replace(value) { matchResult ->
            val options = matchResult.groupValues[1].split("|")
            val chosen = options.random()
            resolveElement(chosen, hopsRemaining)
        }
    }

    /** Resolves a single chosen element: strips ~ prefix and looks up the factoid if present */
    private fun resolveElement(element: String, hopsRemaining: Int): String {
        if (!element.startsWith("~")) return element
        val selector = element.removePrefix("~").trim()
        return resolveReference(selector, hopsRemaining - 1) ?: ""
    }

    /** Looks up a factoid by selector and returns its resolved TEXT value, or null on miss */
    fun resolveReference(selector: String, hopsRemaining: Int): String? {
        if (hopsRemaining <= 0) return null
        val attributes = factoidService.findBySelector(selector)
        val textAttr =
            attributes.firstOrNull { it.attributeType == FactoidAttributeType.TEXT } ?: return null
        val raw = textAttr.attributeValue ?: return null

        // Strip <reply> prefix if present, matching TEXT rendering behavior
        val stripped =
            if (raw.startsWith("<reply>", true)) {
                raw.substring("<reply>".length)
            } else {
                raw
            }

        // Recursively resolve any selection groups in the referenced factoid
        return resolveSelections(stripped, hopsRemaining - 1)
    }
}
