/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.factoid.entity.Factoid
import com.enigmastation.streampack.factoid.entity.FactoidAttribute
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.model.FactoidLinkMetadataResponse
import com.enigmastation.streampack.factoid.model.FindFactoidLinkMetadataRequest
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import com.enigmastation.streampack.factoid.repository.FactoidRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder

@SpringBootTest
class FindFactoidLinkMetadataOperationTests {
    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var factoidRepository: FactoidRepository
    @Autowired lateinit var factoidAttributeRepository: FactoidAttributeRepository

    @BeforeEach
    fun setup() {
        factoidAttributeRepository.deleteAll()
        factoidRepository.deleteAll()
    }

    @Test
    fun `returns text and urls when factoid exists`() {
        val factoid = factoidRepository.save(Factoid(selector = "thing", updatedBy = "test"))
        factoidAttributeRepository.save(
            FactoidAttribute(
                factoid = factoid,
                attributeType = FactoidAttributeType.TEXT,
                attributeValue = "thing is a thing",
                updatedBy = "test",
            )
        )
        factoidAttributeRepository.save(
            FactoidAttribute(
                factoid = factoid,
                attributeType = FactoidAttributeType.URLS,
                attributeValue = "https://thing.com",
                updatedBy = "test",
            )
        )

        val message = MessageBuilder.withPayload(FindFactoidLinkMetadataRequest("thing")).build()
        val result = eventGateway.process(message)

        val success = assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = assertInstanceOf(FactoidLinkMetadataResponse::class.java, success.payload)
        assertEquals("thing", payload.selector)
        assertEquals("thing is a thing", payload.text)
        assertEquals("https://thing.com", payload.urls)
    }

    @Test
    fun `returns empty payload for missing selector`() {
        val message = MessageBuilder.withPayload(FindFactoidLinkMetadataRequest("missing")).build()
        val result = eventGateway.process(message)

        val success = assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = assertInstanceOf(FactoidLinkMetadataResponse::class.java, success.payload)
        assertEquals("missing", payload.selector)
        assertEquals(null, payload.text)
        assertEquals(null, payload.urls)
    }
}
