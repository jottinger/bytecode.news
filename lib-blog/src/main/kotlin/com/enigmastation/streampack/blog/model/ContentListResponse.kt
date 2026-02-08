/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Paginated list of post summaries */
data class ContentListResponse(
    val posts: List<ContentSummary>,
    val page: Int,
    val totalPages: Int,
    val totalCount: Long,
)
