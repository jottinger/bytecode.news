/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.HtmlTitleFetcher
import com.enigmastation.streampack.core.service.TitleFetcher
import com.enigmastation.streampack.urltitle.service.TestTitleFetcher
import com.enigmastation.streampack.urltitle.service.TestTitleFetcherConfiguration
import com.enigmastation.streampack.urltitle.service.UrlTitleService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
@Import(TestTitleFetcherConfiguration::class)
class UrlTitleSummaryTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var titleFetcher: TitleFetcher
@Autowired lateinit var operation: UrlTitleOperation
@Autowired lateinit var service: UrlTitleService
@Autowired lateinit var htmlTitleFetcher: HtmlTitleFetcher
    private val testFetcher: TestTitleFetcher
        get() = titleFetcher as TestTitleFetcher

    @BeforeEach
    fun setUp() {
        testFetcher.clear()
    }

    private fun message(text: String, nick: String = "testuser") =
        MessageBuilder.withPayload(text)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.CONSOLE, serviceId = "", replyTo = "local"),
            )
            .setHeader("nick", nick)
            .build()

    @Test
    fun `single URL produces summary with url singular`() {
        testFetcher.setTitle("https://kotlinlang.org/docs/coroutines", "Coroutines | Kotlin")
        val result = eventGateway.process(message("check https://kotlinlang.org/docs/coroutines"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("mentioned url:"), "Expected singular 'url' in: $payload")
        assertTrue(
            payload.contains("https://kotlinlang.org/docs/coroutines"),
            "Expected URL in: $payload",
        )
        assertTrue(payload.contains("\"Coroutines | Kotlin\""), "Expected title in: $payload")
    }

    @Test
    @EnabledIfSystemProperty(named = "live.tests", matches = "true")
    fun `live fire youtube test`() {
        try {
            service.titleFetcher = htmlTitleFetcher
            val result = eventGateway.process(message("check https://www.youtube.com/watch?v=jNDWnMfDnuw out"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload.contains("mentioned url:"), "Expected singular 'url' in: $payload")
            assertTrue(
                payload.contains("https://www.youtube.com/watch?v=jNDWnMfDnuw"),
                "Expected URL in: $payload",
            )
            assertTrue(payload.contains("\"Top 5 Hollywood"), "Expected title in: $payload")
        } finally {
            service.titleFetcher = titleFetcher
        }
    }

    @Test
    fun `multiple distinct URLs produces summary with urls plural`() {
        testFetcher.setTitle("https://abc.example.com/page1", "Totally Unrelated Title")
        testFetcher.setTitle("https://xyz.example.com/page2", "Another Unrelated Title")
        val result =
            eventGateway.process(
                message("see https://abc.example.com/page1 and https://xyz.example.com/page2")
            )
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("mentioned urls:"), "Expected plural 'urls' in: $payload")
        assertTrue(payload.contains("and"), "Expected 'and' joining in: $payload")
    }

    @Test
    fun `duplicate URLs are deduplicated in summary`() {
        testFetcher.setTitle("https://kotlinlang.org/docs/coroutines", "Coroutines | Kotlin")
        val result =
            eventGateway.process(
                message(
                    "https://kotlinlang.org/docs/coroutines is great https://kotlinlang.org/docs/coroutines"
                )
            )
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        // Should appear exactly once since duplicates are removed
        assertTrue(payload.contains("mentioned url:"), "Expected singular 'url' in: $payload")
        val occurrences = payload.split("https://kotlinlang.org/docs/coroutines").size - 1
        assertTrue(
            occurrences == 1,
            "Expected URL to appear once in summary but found $occurrences times in: $payload",
        )
    }

    @Test
    fun `summary includes nick from message`() {
        testFetcher.setTitle("https://kotlinlang.org/docs/coroutines", "Coroutines | Kotlin")
        val result =
            eventGateway.process(message("https://kotlinlang.org/docs/coroutines", nick = "alice"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.startsWith("alice mentioned"), "Expected nick prefix in: $payload")
    }

    @Test
    fun `URLs with no fetchable title are excluded from summary`() {
        testFetcher.setTitle("https://abc.example.com/page1", "Totally Unrelated Title")
        // second URL has no title registered in the test fetcher
        val result =
            eventGateway.process(
                message("https://abc.example.com/page1 and https://nope.example.com/missing")
            )
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("mentioned url:"), "Expected singular 'url' in: $payload")
        assertTrue(
            !payload.contains("nope.example.com"),
            "URL with no title should be excluded from: $payload",
        )
    }
}
