/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.time.Instant
import java.util.UUID

/** Tree node representing a comment in a threaded discussion */
data class CommentNode(
    val id: UUID,
    val authorId: UUID?,
    val authorDisplayName: String,
    val renderedHtml: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deleted: Boolean,
    val editable: Boolean,
    val children: List<CommentNode>,
)
