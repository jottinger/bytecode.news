/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/** Lightweight post representation for listing pages */
@Schema(description = "Post summary for listing and search result pages")
data class ContentSummary(
    @Schema(description = "Post UUIDv7") val id: UUID,
    @Schema(description = "Post title") val title: String,
    @Schema(description = "URL slug path", example = "2026/03/understanding-virtual-threads")
    val slug: String,
    @Schema(description = "First ~200 characters of rendered text") val excerpt: String?,
    @Schema(description = "Display name of the author") val authorDisplayName: String,
    @Schema(description = "Publication timestamp, null for unpublished drafts")
    val publishedAt: Instant?,
    @Schema(description = "Number of non-deleted comments") val commentCount: Int = 0,
    @Schema(description = "Tag names") val tags: List<String> = emptyList(),
    @Schema(description = "Category names") val categories: List<String> = emptyList(),
)
