/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.service

import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import com.enigmastation.streampack.factoid.repository.FactoidRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FactoidServiceTests {

    @Autowired lateinit var factoidService: FactoidService
    @Autowired lateinit var factoidRepository: FactoidRepository
    @Autowired lateinit var factoidAttributeRepository: FactoidAttributeRepository

    @Test
    fun `create factoid with TEXT attribute`() {
        val result =
            factoidService.save("spring", FactoidAttributeType.TEXT, "A Java framework", "testuser")
        assertEquals(FactoidService.SaveResult.Ok, result)

        val attrs = factoidService.findBySelector("spring")
        assertEquals(1, attrs.size)
        assertEquals(FactoidAttributeType.TEXT, attrs[0].attributeType)
        assertEquals("A Java framework", attrs[0].attributeValue)
    }

    @Test
    fun `update existing attribute performs upsert`() {
        factoidService.save("kotlin", FactoidAttributeType.TEXT, "A JVM language", "user1")
        factoidService.save("kotlin", FactoidAttributeType.TEXT, "A modern JVM language", "user2")

        val attrs = factoidService.findBySelector("kotlin")
        assertEquals(1, attrs.size)
        assertEquals("A modern JVM language", attrs[0].attributeValue)
        assertEquals("user2", attrs[0].updatedBy)
    }

    @Test
    fun `lock prevents modification`() {
        factoidService.save("locked-item", FactoidAttributeType.TEXT, "original", "user1")
        factoidService.setLocked("locked-item", true)

        val result =
            factoidService.save("locked-item", FactoidAttributeType.TEXT, "modified", "user2")
        assertTrue(result is FactoidService.SaveResult.Locked)

        val attrs = factoidService.findBySelector("locked-item")
        assertEquals("original", attrs[0].attributeValue)
    }

    @Test
    fun `unlock allows modification`() {
        factoidService.save("unlock-item", FactoidAttributeType.TEXT, "original", "user1")
        factoidService.setLocked("unlock-item", true)
        factoidService.setLocked("unlock-item", false)

        val result =
            factoidService.save("unlock-item", FactoidAttributeType.TEXT, "modified", "user2")
        assertEquals(FactoidService.SaveResult.Ok, result)

        val attrs = factoidService.findBySelector("unlock-item")
        assertEquals("modified", attrs[0].attributeValue)
    }

    @Test
    fun `delete cascades to attributes`() {
        factoidService.save("deleteme", FactoidAttributeType.TEXT, "gone soon", "user1")
        factoidService.save("deleteme", FactoidAttributeType.URLS, "http://example.com", "user1")

        factoidService.deleteSelector("deleteme")

        assertTrue(factoidService.findBySelector("deleteme").isEmpty())
        assertNull(factoidRepository.findBySelectorIgnoreCase("deleteme"))
    }

    @Test
    fun `case insensitive lookup`() {
        factoidService.save("CamelCase", FactoidAttributeType.TEXT, "test value", "user1")

        val attrs = factoidService.findBySelector("camelcase")
        assertEquals(1, attrs.size)
        assertEquals("test value", attrs[0].attributeValue)
    }

    @Test
    fun `progressive selector search finds longest match`() {
        factoidService.save("ask", FactoidAttributeType.TEXT, "Ask about \$1", "user1")
        factoidService.save("ask joe", FactoidAttributeType.TEXT, "Ask Joe about \$1", "user1")

        val result = factoidService.findSelectorWithArguments("ask joe about kotlin")
        assertNotNull(result)
        assertEquals("ask joe", result!!.first)
        assertEquals("about kotlin", result.second)
    }

    @Test
    fun `progressive search with no arguments`() {
        factoidService.save("spring", FactoidAttributeType.TEXT, "A Java framework", "user1")

        val result = factoidService.findSelectorWithArguments("spring")
        assertNotNull(result)
        assertEquals("spring", result!!.first)
        assertEquals("", result.second)
    }

    @Test
    fun `search by term finds matching selectors`() {
        factoidService.save("spring boot", FactoidAttributeType.TEXT, "A framework", "user1")
        factoidService.save("kotlin", FactoidAttributeType.TEXT, "A JVM language", "user1")

        val results = factoidService.searchForTerm("spring")
        assertTrue(results.contains("spring boot"))
    }

    @Test
    fun `search returns empty list for no matches`() {
        val results = factoidService.searchForTerm("nonexistent")
        assertTrue(results.isEmpty())
    }
}
