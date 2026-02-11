/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.specs.service

import com.enigmastation.streampack.specs.model.SpecType
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SpecLookupServiceTests {

    @Autowired lateinit var lookupService: SpecLookupService

    private lateinit var httpServer: HttpServer
    private var baseUrl: String = ""

    @BeforeEach
    fun setUp() {
        httpServer = HttpServer.create(InetSocketAddress(0), 0)
        httpServer.start()
        baseUrl = "http://localhost:${httpServer.address.port}"
    }

    @AfterEach
    fun tearDown() {
        httpServer.stop(0)
    }

    @Test
    fun `extracts RFC title from HTML page`() {
        httpServer.createContext("/rfc") { exchange ->
            val html = rfcHtml(2812, "Internet Relay Chat: Client Protocol")
            exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(html.toByteArray()) }
        }

        val title = lookupService.lookupUrl("$baseUrl/rfc", SpecType.RFC)
        assertNotNull(title)
        assertEquals("Internet Relay Chat: Client Protocol", title)
    }

    @Test
    fun `extracts JEP title from HTML page`() {
        httpServer.createContext("/jep") { exchange ->
            val html = jepHtml(3, "JDK Release Process")
            exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(html.toByteArray()) }
        }

        val title = lookupService.lookupUrl("$baseUrl/jep", SpecType.JEP)
        assertNotNull(title)
        assertEquals("JDK Release Process", title)
    }

    @Test
    fun `extracts JSR title from HTML page`() {
        httpServer.createContext("/jsr") { exchange ->
            val html = jsrHtml(3, "Java Management Extensions (JMX) Specification")
            exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(html.toByteArray()) }
        }

        val title = lookupService.lookupUrl("$baseUrl/jsr", SpecType.JSR)
        assertNotNull(title)
        assertEquals("Java Management Extensions (JMX) Specification", title)
    }

    @Test
    fun `returns null for 404 response`() {
        httpServer.createContext("/missing") { exchange -> exchange.sendResponseHeaders(404, -1) }

        val title = lookupService.lookupUrl("$baseUrl/missing", SpecType.RFC)
        assertNull(title)
    }

    @Test
    fun `returns null for unreachable server`() {
        val title = lookupService.lookupUrl("http://localhost:1/unreachable", SpecType.RFC)
        assertNull(title)
    }

    @Test
    fun `returns null for page with no matching element`() {
        httpServer.createContext("/empty") { exchange ->
            val html = "<html><head></head><body>No title here</body></html>"
            exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(html.toByteArray()) }
        }

        val title = lookupService.lookupUrl("$baseUrl/empty", SpecType.JSR)
        assertNull(title)
    }

    companion object {
        fun rfcHtml(number: Int, title: String): String =
            """
            <html>
            <head><title>RFC $number - $title</title></head>
            <body><h1>$title</h1></body>
            </html>
            """
                .trimIndent()

        fun jepHtml(number: Int, title: String): String =
            """
            <html>
            <head><title>JEP $number: $title</title></head>
            <body><h1>$title</h1></body>
            </html>
            """
                .trimIndent()

        fun jsrHtml(number: Int, title: String): String =
            """
            <html>
            <head><title>JSR Page</title></head>
            <body><div class="header1">JSR $number: $title</div></body>
            </html>
            """
                .trimIndent()
    }
}
