/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/** Result of successfully creating a new blog post */
@Schema(description = "Confirmation of a newly created blog post draft")
data class CreateContentResponse(
    @Schema(description = "Post UUIDv7") val id: UUID,
    @Schema(description = "Post title as submitted") val title: String,
    @Schema(description = "Generated URL slug", example = "2026/03/understanding-virtual-threads")
    val slug: String,
    @Schema(description = "First ~200 characters of rendered text, or null if empty")
    val excerpt: String?,
    @Schema(description = "Always DRAFT for newly created posts") val status: PostStatus,
    @Schema(description = "Author UUIDv7, or null for anonymous submissions") val authorId: UUID?,
    @Schema(description = "Display name of the author, or 'Anonymous'")
    val authorDisplayName: String,
    @Schema(description = "ISO 8601 creation timestamp") val createdAt: Instant,
    @Schema(description = "Tag names associated with the post")
    val tags: List<String> = emptyList(),
    @Schema(description = "Category names associated with the post")
    val categories: List<String> = emptyList(),
)
