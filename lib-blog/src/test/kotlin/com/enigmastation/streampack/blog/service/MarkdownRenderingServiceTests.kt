/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MarkdownRenderingServiceTests {
    val logger = LoggerFactory.getLogger(this::class.java)
    @Autowired lateinit var markdownRenderingService: MarkdownRenderingService

    @Test
    fun `headings and paragraphs render correctly`() {
        val result = markdownRenderingService.render("# Hello\n\nThis is a paragraph.")
        assertTrue(result.contains("<h1>Hello</h1>"))
        assertTrue(result.contains("<p>This is a paragraph.</p>"))
    }

    @Test
    fun `tables extension works`() {
        val markdown =
            """
            | Name | Age |
            |------|-----|
            | Alice | 30 |
            | Bob | 25 |
            """
                .trimIndent()
        val result = markdownRenderingService.render(markdown)
        assertTrue(result.contains("<table>"))
        assertTrue(result.contains("<th>Name</th>"))
        assertTrue(result.contains("<td>Alice</td>"))
    }

    @Test
    fun `autolinks convert URLs`() {
        val result = markdownRenderingService.render("Visit https://example.com for more info.")
        assertTrue(result.contains("<a href=\"https://example.com\">"))
    }

    @Test
    fun `raw HTML is escaped`() {
        val result = markdownRenderingService.render("Hello <script>alert('xss')</script> world")
        // Raw HTML should be escaped, not rendered as actual tags
        assertTrue(!result.contains("<script>"))
        assertTrue(result.contains("&lt;script&gt;"))
    }

    @Test
    fun `excerpt truncates at word boundary`() {
        val longText = "This is a sentence with enough words to exceed the limit. ".repeat(5)
        val result = markdownRenderingService.excerpt(longText, 50)
        assertTrue(result.length <= 53) // 50 + "..."
        assertTrue(result.endsWith("..."))
        // Should not cut in the middle of a word
        assertTrue(!result.dropLast(3).endsWith(" "))
    }

    @Test
    fun `excerpt returns full text when short enough`() {
        val result = markdownRenderingService.excerpt("Short text.", 200)
        assertEquals("Short text.", result)
    }

    @Test
    fun `empty input produces empty output`() {
        assertEquals("", markdownRenderingService.render(""))
        assertEquals("", markdownRenderingService.render("   "))
        assertEquals("", markdownRenderingService.excerpt(""))
        assertEquals("", markdownRenderingService.excerpt("   "))
    }

    @Test
    fun `excerpt strips markdown formatting`() {
        val result = markdownRenderingService.excerpt("# Heading\n\n**Bold** and *italic* text.")
        // Should not contain markdown or HTML artifacts
        assertTrue(!result.contains("#"))
        assertTrue(!result.contains("**"))
        assertTrue(!result.contains("<"))
        assertTrue(result.contains("Bold"))
        assertTrue(result.contains("italic"))
    }

    @Test
    fun `excerpt normalizes punctuation spacing`() {
        val markdown =
            "When a base builder class returns `this`, subclass methods lose type information."
        val result = markdownRenderingService.excerpt(markdown, maxLength = 500, maxSentences = 3)
        assertTrue(result.contains("this, subclass"))
        assertTrue(!result.contains("this ,"))
    }

    @Test
    fun `excerpt normalizes hyphen spacing`() {
        val markdown = "The strategy is simple (so 4- player choice each round)."
        val result = markdownRenderingService.excerpt(markdown, maxLength = 500, maxSentences = 3)
        assertTrue(result.contains("4-player"))
        assertTrue(!result.contains("4- player"))
    }

    @Test
    fun `excerpt prefers three sentence summary`() {
        val markdown =
            """
            Kotlin is practical for backend systems.
            It integrates well with existing Java libraries.
            This project also uses Clojure interop for selected components.
            Excerpts should include the most representative points.
            Extra details can stay in the full article.
            """
                .trimIndent()
        val result = markdownRenderingService.excerpt(markdown, maxLength = 1000, maxSentences = 3)
        val sentenceCount = Regex("[.!?](\\s|$)").findAll(result).count()
        logger.info("Result: {}", result)
        assertTrue(sentenceCount <= 3)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("Clojure interop"))
    }

    @Test
    fun `fenced code blocks render correctly`() {
        val markdown =
            """
            ```kotlin
            fun main() = println("Hello")
            ```
            """
                .trimIndent()
        val result = markdownRenderingService.render(markdown)
        assertTrue(result.contains("<code"))
        assertTrue(result.contains("fun main()"))
    }

    @Test
    fun `gfm strikethrough renders correctly`() {
        val result = markdownRenderingService.render("Use ~~old~~ new")
        assertTrue(result.contains("<del>old</del>"))
    }

    @Test
    fun `gfm task list renders correctly`() {
        val result = markdownRenderingService.render("- [x] done\n- [ ] todo")
        assertTrue(result.contains("<ul>"))
        assertTrue(result.contains("type=\"checkbox\""))
        assertTrue(result.contains("done"))
        assertTrue(result.contains("todo"))
    }
}
