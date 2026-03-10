/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AiJsonParserTests {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `parse recovers trailing comma json`() {
        val raw =
            """
            {
              "title": "F-Bounded Polymorphism: Type-Safe Builders in Java",
              "summary": "A summary of the article.",
              "tags": ["java", "generics", "builders"],
            }
            """
                .trimIndent()

        val parsed = AiJsonParser.parse(raw, mapper)
        assertNotNull(parsed)
        assertEquals(
            "F-Bounded Polymorphism: Type-Safe Builders in Java",
            parsed!!.path("title").asText(),
        )
        assertEquals("A summary of the article.", parsed.path("summary").asText())
        assertEquals(3, parsed.path("tags").size())
    }

    @Test
    fun `parse handles fenced json with trailing comma`() {
        val raw =
            """
            ```json
            {
              "title": "Example",
              "summary": "Example summary.",
              "tags": ["kotlin", "spring"],
            }
            ```
            """
                .trimIndent()

        val parsed = AiJsonParser.parse(raw, mapper)
        assertNotNull(parsed)
        assertEquals("Example", parsed!!.path("title").asText())
        assertEquals("Example summary.", parsed.path("summary").asText())
        assertEquals(2, parsed.path("tags").size())
    }
}
