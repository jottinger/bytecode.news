/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/** Full post representation for single-post views */
@Schema(description = "Full post detail including rendered HTML and metadata")
data class ContentDetail(
    @Schema(description = "Post UUIDv7") val id: UUID,
    @Schema(description = "Post title") val title: String,
    @Schema(description = "URL slug path", example = "2026/03/understanding-virtual-threads")
    val slug: String,
    @Schema(description = "Post content rendered as HTML from Markdown source")
    val renderedHtml: String,
    @Schema(description = "First ~200 characters of rendered text") val excerpt: String?,
    @Schema(description = "Author UUIDv7, or null for anonymous submissions") val authorId: UUID?,
    @Schema(description = "Display name of the author") val authorDisplayName: String,
    @Schema(description = "DRAFT or APPROVED") val status: PostStatus,
    @Schema(description = "Scheduled or actual publication timestamp, null for unpublished drafts")
    val publishedAt: Instant?,
    @Schema(description = "ISO 8601 creation timestamp") val createdAt: Instant,
    @Schema(description = "ISO 8601 last-modified timestamp") val updatedAt: Instant,
    @Schema(description = "Number of non-deleted comments on this post") val commentCount: Int = 0,
    @Schema(description = "Tag names") val tags: List<String> = emptyList(),
    @Schema(description = "Category names") val categories: List<String> = emptyList(),
    @Schema(
        description =
            "Raw Markdown source. Only included when the requester is the author or an admin."
    )
    val markdownSource: String? = null,
)
