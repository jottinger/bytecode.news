/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import

/** Fetches the OpenAPI spec from the running app and writes it to docs/openapi.json */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelConfiguration::class)
class OpenApiGeneratorTest {

    @LocalServerPort private var port: Int = 0

    @Test
    fun `generate OpenAPI spec`() {
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/v3/api-docs"))
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Failed to fetch OpenAPI spec: ${response.statusCode()}"
        }

        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val root = mapper.readTree(response.body()) as ObjectNode

        // Replace random test port with a stable placeholder
        val servers = mapper.createArrayNode()
        val server = mapper.createObjectNode()
        server.put("url", "http://localhost:8080")
        server.put("description", "Local development server")
        servers.add(server)
        root.set<ArrayNode>("servers", servers)

        val docsDir = Path.of("../docs")
        docsDir.toFile().mkdirs()
        docsDir.resolve("openapi.json").toFile().writeText(mapper.writeValueAsString(root))
    }
}
