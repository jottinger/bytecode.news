/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.service

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.springframework.stereotype.Service

/** Converts markdown source to sanitized HTML and generates plain-text excerpts */
@Service
class MarkdownRenderingService {

    private val parser: Parser
    private val renderer: HtmlRenderer

    init {
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, listOf(TablesExtension.create(), AutolinkExtension.create()))
        // Strip raw HTML tags from markdown input
        options.set(HtmlRenderer.ESCAPE_HTML, true)
        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

    /** Render markdown source to HTML with raw HTML escaped */
    fun render(markdownSource: String): String {
        if (markdownSource.isBlank()) return ""
        val document = parser.parse(markdownSource)
        return renderer.render(document).trim()
    }

    /** Generate a plain-text excerpt by stripping markup and truncating at word boundary */
    fun excerpt(markdownSource: String, maxLength: Int = 200): String {
        if (markdownSource.isBlank()) return ""
        val plainText = stripMarkup(markdownSource)
        if (plainText.length <= maxLength) return plainText
        // Truncate at last space before maxLength to avoid cutting words
        val truncated = plainText.substring(0, maxLength)
        val lastSpace = truncated.lastIndexOf(' ')
        return if (lastSpace > 0) truncated.substring(0, lastSpace) + "..." else truncated + "..."
    }

    /** Remove markdown syntax and HTML tags to produce plain text */
    private fun stripMarkup(markdownSource: String): String {
        // Render to HTML first, then strip all tags
        val document = parser.parse(markdownSource)
        val html = renderer.render(document)
        return html
            .replace(Regex("<[^>]+>"), "") // strip HTML tags
            .replace(Regex("&[a-zA-Z]+;"), " ") // replace HTML entities with space
            .replace(Regex("\\s+"), " ") // collapse whitespace
            .trim()
    }
}
