/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** Response containing a threaded comment tree for a post */
data class CommentThreadResponse(
    val postId: UUID,
    val comments: List<CommentNode>,
    val totalActiveCount: Int,
)
