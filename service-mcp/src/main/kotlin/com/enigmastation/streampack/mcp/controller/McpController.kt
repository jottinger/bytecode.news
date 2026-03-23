/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.mcp.controller

import com.enigmastation.streampack.core.json.JacksonMappers
import com.enigmastation.streampack.mcp.service.McpToolService
import com.enigmastation.streampack.mcp.service.ToolResult
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Minimal JSON-RPC MCP endpoint for read-only bytecode.news tools. */
@RestController
class McpController(private val toolService: McpToolService) {
    private val mapper = JacksonMappers.standard()
    private val protocolVersion = "2024-11-05"

    @PostMapping(
        "/mcp",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun handle(@RequestBody request: JsonRpcRequest): ResponseEntity<Any> {
        val id = request.id
        val method = request.method

        if (request.jsonrpc != "2.0" || method.isNullOrBlank()) {
            return ResponseEntity.ok(error(id, -32600, "Invalid Request"))
        }

        val response: Any =
            when (method) {
                "initialize" -> success(id, initializeResult())
                "tools/list" -> success(id, toolsListResult())
                "tools/call" -> handleToolCall(id, request.params)
                else -> error(id, -32601, "Method not found: $method")
            }
        return ResponseEntity.ok(response)
    }

    private fun initializeResult() =
        InitializeResult(
            protocolVersion = protocolVersion,
            serverInfo = ServerInfo(name = "bytecode.news-mcp", version = "1.0"),
            capabilities = ServerCapabilities(tools = ToolCapabilities(listChanged = false)),
        )

    private fun toolsListResult() =
        ToolsListResult(
            tools =
                listOf(
                    toolDefinition(
                        name = "search_posts",
                        description = "Search approved public blog posts by query.",
                        properties =
                            mapOf(
                                "query" to schema("string", "Search query string"),
                                "page" to schema("integer", "Zero-based page index", 0),
                                "size" to schema("integer", "Page size (1-100)", 20),
                            ),
                        required = listOf("query"),
                    ),
                    toolDefinition(
                        name = "get_post",
                        description = "Get a single public post by UUID, slug path, or post URL.",
                        properties =
                            mapOf(
                                "postRef" to
                                    schema("string", "UUID, YYYY/MM/slug, or full post URL")
                            ),
                        required = listOf("postRef"),
                    ),
                    toolDefinition(
                        name = "list_factoids",
                        description = "List factoids with pagination.",
                        properties =
                            mapOf(
                                "page" to schema("integer", "Zero-based page index", 0),
                                "size" to schema("integer", "Page size (1-100)", 20),
                            ),
                    ),
                    toolDefinition(
                        name = "get_factoid",
                        description = "Get factoid detail and structured attributes by selector.",
                        properties = mapOf("selector" to schema("string", "Factoid selector")),
                        required = listOf("selector"),
                    ),
                    toolDefinition(
                        name = "search_factoids",
                        description = "Search factoids by selector text.",
                        properties =
                            mapOf(
                                "query" to schema("string", "Search query string"),
                                "page" to schema("integer", "Zero-based page index", 0),
                                "size" to schema("integer", "Page size (1-100)", 20),
                            ),
                        required = listOf("query"),
                    ),
                    toolDefinition(
                        name = "list_taxonomy",
                        description = "List aggregate blog/factoid tags and categories.",
                    ),
                    toolDefinition(
                        name = "factoid_write_reference",
                        description =
                            "Get command syntax and examples for creating factoids outside MCP.",
                    ),
                )
        )

    private fun handleToolCall(id: Any?, params: Map<String, Any?>?): Any {
        if (params == null) {
            return error(id, -32602, "Invalid params")
        }

        val toolCall =
            runCatching { mapper.convertValue(params, ToolCallParams::class.java) }
                .getOrElse {
                    return error(id, -32602, "Invalid tool call params")
                }

        if (toolCall.name.isBlank()) {
            return error(id, -32602, "Missing tool name")
        }

        val result =
            when (toolCall.name) {
                "search_posts" -> {
                    val args = convertArgs<SearchPostsArgs>(toolCall.arguments)
                    toolService.searchPosts(query = args.query, page = args.page, size = args.size)
                }
                "get_post" -> {
                    val args = convertArgs<GetPostArgs>(toolCall.arguments)
                    toolService.getPost(args.postRef)
                }
                "list_factoids" -> {
                    val args = convertArgs<ListFactoidsArgs>(toolCall.arguments)
                    toolService.listFactoids(page = args.page, size = args.size)
                }
                "get_factoid" -> {
                    val args = convertArgs<GetFactoidArgs>(toolCall.arguments)
                    toolService.getFactoid(args.selector)
                }
                "search_factoids" -> {
                    val args = convertArgs<SearchFactoidsArgs>(toolCall.arguments)
                    toolService.searchFactoids(
                        query = args.query,
                        page = args.page,
                        size = args.size,
                    )
                }
                "list_taxonomy" -> toolService.listTaxonomy()
                "factoid_write_reference" -> toolService.factoidWriteReference()
                else -> return error(id, -32601, "Unknown tool: ${toolCall.name}")
            }

        return success(id, encodeToolResult(result))
    }

    private inline fun <reified T> convertArgs(arguments: Map<String, Any?>): T {
        return mapper.convertValue(arguments, T::class.java)
    }

    private fun encodeToolResult(result: ToolResult): ToolCallResult {
        return if (result.ok) {
            val structured = result.payload
            ToolCallResult(
                isError = false,
                structuredContent = structured,
                content = listOf(ToolCallContent(text = mapper.writeValueAsString(structured))),
            )
        } else {
            ToolCallResult(
                isError = true,
                structuredContent = null,
                content = listOf(ToolCallContent(text = result.error ?: "Tool failed")),
            )
        }
    }

    private fun toolDefinition(
        name: String,
        description: String,
        properties: Map<String, JsonSchemaProperty> = emptyMap(),
        required: List<String> = emptyList(),
    ) =
        ToolDefinition(
            name = name,
            description = description,
            inputSchema =
                JsonSchemaObject(
                    type = "object",
                    properties = properties,
                    required = required,
                    additionalProperties = false,
                ),
        )

    private fun schema(type: String, description: String, defaultValue: Int? = null) =
        JsonSchemaProperty(type = type, description = description, default = defaultValue)

    private fun success(id: Any?, result: Any): JsonRpcSuccess =
        JsonRpcSuccess(id = id, result = result)

    private fun error(id: Any?, code: Int, message: String): JsonRpcErrorResponse =
        JsonRpcErrorResponse(id = id, error = JsonRpcError(code = code, message = message))
}
