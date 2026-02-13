/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.service

import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/** Integration tests for FactoidTextRenderer selection and reference resolution */
@SpringBootTest
@Transactional
class FactoidTextRendererTests {

    @Autowired lateinit var renderer: FactoidTextRenderer
    @Autowired lateinit var factoidService: FactoidService

    @Test
    fun `text without selections passes through unchanged`() {
        assertEquals("hello world", renderer.resolveSelections("hello world", 3))
    }

    @Test
    fun `parentheses without pipe pass through unchanged`() {
        assertEquals("(just a note)", renderer.resolveSelections("(just a note)", 3))
    }

    @Test
    fun `selection group picks from options`() {
        val validResults = setOf("hello", "world")
        repeat(20) {
            val result = renderer.resolveSelections("(hello|world)", 3)
            assertTrue(result in validResults)
        }
    }

    @Test
    fun `multiple selection groups resolve independently`() {
        val validA = setOf("a", "b")
        val validC = setOf("c", "d")
        repeat(20) {
            val result = renderer.resolveSelections("(a|b) and (c|d)", 3)
            val parts = result.split(" and ")
            assertEquals(2, parts.size)
            assertTrue(parts[0] in validA)
            assertTrue(parts[1] in validC)
        }
    }

    @Test
    fun `tilde reference resolves factoid text`() {
        factoidService.save("target", FactoidAttributeType.TEXT, "resolved value", "test")

        val validResults = setOf("resolved value", "literal")
        repeat(20) {
            val result = renderer.resolveSelections("(~target|literal)", 3)
            assertTrue(result in validResults)
        }
    }

    @Test
    fun `tilde reference strips reply prefix`() {
        factoidService.save("target", FactoidAttributeType.TEXT, "<reply>clean value", "test")

        val result = renderer.resolveReference("target", 3)
        assertEquals("clean value", result)
    }

    @Test
    fun `tilde reference miss returns empty string in selection`() {
        // Force the ~ path with only tilde references
        val result = renderer.resolveSelections("(~missing|~missing)", 3)
        assertEquals("", result)
    }

    @Test
    fun `hops exhausted returns value unchanged`() {
        assertEquals("(~target|literal)", renderer.resolveSelections("(~target|literal)", 0))
    }

    @Test
    fun `resolveReference returns null when hops exhausted`() {
        assertNull(renderer.resolveReference("anything", 0))
    }

    @Test
    fun `resolveReference returns null for missing factoid`() {
        assertNull(renderer.resolveReference("missing", 3))
    }

    @Test
    fun `chained reference resolution works`() {
        factoidService.save("inner", FactoidAttributeType.TEXT, "<reply>deep value", "test")
        factoidService.save("outer", FactoidAttributeType.TEXT, "<reply>(~inner|~inner)", "test")

        val result = renderer.resolveReference("outer", 3)
        assertEquals("deep value", result)
    }
}
