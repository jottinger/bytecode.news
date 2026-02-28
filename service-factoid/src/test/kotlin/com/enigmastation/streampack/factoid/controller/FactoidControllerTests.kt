/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.controller

import com.enigmastation.streampack.factoid.config.TestSecurityConfiguration
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.repository.FactoidRepository
import com.enigmastation.streampack.factoid.service.FactoidService
import com.enigmastation.streampack.test.TestChannelConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/** Integration tests for the read-only factoid REST endpoints */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class, TestSecurityConfiguration::class)
class FactoidControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var factoidService: FactoidService
    @Autowired lateinit var factoidRepository: FactoidRepository

    @BeforeEach
    fun setUp() {
        factoidService.save("spring", FactoidAttributeType.TEXT, "A Java framework", "testuser")
        factoidService.save("spring", FactoidAttributeType.URLS, "https://spring.io", "testuser")
        factoidService.save("kotlin", FactoidAttributeType.TEXT, "A JVM language", "testuser")
        factoidService.save(
            "kotlin",
            FactoidAttributeType.MAVEN,
            "org.jetbrains.kotlin:kotlin-stdlib",
            "testuser",
        )
        factoidService.save("java", FactoidAttributeType.TEXT, "The OG JVM language", "testuser")
    }

    @Test
    fun `GET factoids returns paginated listing`() {
        mockMvc.get("/factoids").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(3) }
            jsonPath("$.content[0].selector") { value("java") }
            jsonPath("$.content[1].selector") { value("kotlin") }
            jsonPath("$.content[2].selector") { value("spring") }
            jsonPath("$.totalElements") { value(3) }
        }
    }

    @Test
    fun `GET factoids with page size limits results`() {
        mockMvc.get("/factoids?size=2").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.totalElements") { value(3) }
            jsonPath("$.totalPages") { value(2) }
        }
    }

    @Test
    fun `GET factoids with search query filters results`() {
        mockMvc.get("/factoids?q=spring").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].selector") { value("spring") }
        }
    }

    @Test
    fun `GET factoids with search query no matches returns empty page`() {
        mockMvc.get("/factoids?q=nonexistent").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(0) }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `GET factoid detail returns all summary attributes`() {
        mockMvc.get("/factoids/spring").andExpect {
            status { isOk() }
            jsonPath("$.selector") { value("spring") }
            jsonPath("$.locked") { value(false) }
            jsonPath("$.updatedBy") { value("testuser") }
            jsonPath("$.attributes.length()") { value(2) }
        }
    }

    @Test
    fun `GET factoid detail excludes non-summary attributes`() {
        mockMvc.get("/factoids/kotlin").andExpect {
            status { isOk() }
            jsonPath("$.selector") { value("kotlin") }
            jsonPath("$.attributes.length()") { value(1) }
            jsonPath("$.attributes[0].type") { value("text") }
        }
    }

    @Test
    fun `GET factoid detail returns rendered attribute values`() {
        mockMvc.get("/factoids/spring").andExpect {
            status { isOk() }
            jsonPath("$.attributes[0].type") { value("text") }
            jsonPath("$.attributes[0].rendered") { isNotEmpty() }
            jsonPath("$.attributes[1].type") { value("urls") }
            jsonPath("$.attributes[1].rendered") { isNotEmpty() }
        }
    }

    @Test
    fun `GET nonexistent factoid returns 404`() {
        mockMvc.get("/factoids/nonexistent").andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET factoid detail is case insensitive`() {
        mockMvc.get("/factoids/SPRING").andExpect {
            status { isOk() }
            jsonPath("$.selector") { value("spring") }
        }
    }

    // -- Access tracking --

    @Test
    fun `GET factoid detail includes access tracking fields`() {
        // Response reflects entity state at query time, before the increment
        mockMvc.get("/factoids/spring").andExpect {
            status { isOk() }
            jsonPath("$.accessCount") { value(0) }
            jsonPath("$.lastAccessedAt") { doesNotExist() }
        }
        // Second request sees the first request's increment
        mockMvc.get("/factoids/spring").andExpect {
            status { isOk() }
            jsonPath("$.accessCount") { value(1) }
            jsonPath("$.lastAccessedAt") { isNotEmpty() }
        }
    }

    @Test
    fun `GET factoid detail increments access count`() {
        mockMvc.get("/factoids/spring").andExpect { status { isOk() } }
        factoidRepository.flush()

        val factoid = factoidRepository.findBySelectorIgnoreCase("spring")!!
        org.junit.jupiter.api.Assertions.assertEquals(1, factoid.accessCount)
        org.junit.jupiter.api.Assertions.assertNotNull(factoid.lastAccessedAt)
    }

    @Test
    fun `GET factoid listing includes access tracking fields`() {
        mockMvc.get("/factoids").andExpect {
            status { isOk() }
            jsonPath("$.content[0].accessCount") { value(0) }
            jsonPath("$.content[0].lastAccessedAt") { doesNotExist() }
        }
    }
}
