/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema

/** Paginated list of post summaries */
@Schema(description = "Paginated list of post summaries")
data class ContentListResponse(
    @Schema(description = "Post summaries for the current page") val posts: List<ContentSummary>,
    @Schema(description = "Current zero-based page index", example = "0") val page: Int,
    @Schema(description = "Total number of pages available", example = "3") val totalPages: Int,
    @Schema(description = "Total number of matching posts across all pages", example = "42")
    val totalCount: Long,
)
