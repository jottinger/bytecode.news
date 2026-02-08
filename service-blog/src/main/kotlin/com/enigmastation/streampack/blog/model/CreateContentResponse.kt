/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.time.Instant
import java.util.UUID

/** Result of successfully creating a new blog post */
data class CreateContentResponse(
    val id: UUID,
    val title: String,
    val slug: String,
    val excerpt: String?,
    val status: PostStatus,
    val authorId: UUID,
    val authorDisplayName: String,
    val createdAt: Instant,
)
