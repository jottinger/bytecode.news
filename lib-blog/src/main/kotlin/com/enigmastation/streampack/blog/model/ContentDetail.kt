/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.time.Instant
import java.util.UUID

/** Full post representation for single-post views */
data class ContentDetail(
    val id: UUID,
    val title: String,
    val slug: String,
    val renderedHtml: String,
    val excerpt: String?,
    val authorId: UUID?,
    val authorDisplayName: String,
    val status: PostStatus,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val commentCount: Int = 0,
)
