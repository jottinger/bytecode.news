/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.features.controller

import com.enigmastation.streampack.features.config.TestSecurityConfiguration
import com.enigmastation.streampack.test.TestChannelConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Integration tests for the feature discovery endpoint */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestChannelConfiguration::class, TestSecurityConfiguration::class)
class FeaturesControllerTests {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `GET features returns 200 with expected structure`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            jsonPath("$.version") { exists() }
            jsonPath("$.version.name") { exists() }
            jsonPath("$.authentication") { exists() }
            jsonPath("$.authentication.otp") { isBoolean() }
            jsonPath("$.operationGroups") { isArray() }
            jsonPath("$.adapters") { isArray() }
            jsonPath("$.ai") { isBoolean() }
        }
    }

    @Test
    fun `GET features returns sorted operation groups`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            jsonPath("$.operationGroups") { isNotEmpty() }
            jsonPath("$.operationGroups[0]") { exists() }
        }
    }

    @Test
    fun `GET features includes Cache-Control header`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=3600, public") }
        }
    }

    @Test
    fun `GET features version section has name`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            jsonPath("$.version.name") { isNotEmpty() }
        }
    }

    @Test
    fun `GET features authentication reports otp availability`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            jsonPath("$.authentication.otp") { isBoolean() }
        }
    }

    @Test
    fun `GET features ai field is boolean`() {
        mockMvc.get("/features").andExpect {
            status { isOk() }
            jsonPath("$.ai") { isBoolean() }
        }
    }
}
