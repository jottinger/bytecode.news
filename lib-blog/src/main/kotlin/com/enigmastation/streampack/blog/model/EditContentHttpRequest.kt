/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/** HTTP request body for editing a post (id resolved from path) */
@Schema(
    description = "Request body for editing an existing post. All fields replace current values."
)
data class EditContentHttpRequest(
    @Schema(description = "Updated post title", example = "Updated Title") val title: String? = "",
    @Schema(
        description = "Updated post content in Markdown. No raw HTML allowed.",
        example = "Updated content...",
    )
    val markdownSource: String? = "",
    @Schema(
        description = "Replacement tag list. Replaces all existing tag associations.",
        example = "[\"java\", \"concurrency\", \"loom\"]",
    )
    val tags: List<String>? = emptyList(),
    @Schema(description = "Replacement category UUIDv7 list. Replaces all existing associations.")
    val categoryIds: List<UUID>? = emptyList(),
)
