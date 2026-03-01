/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** HTTP request body for creating a comment (postId resolved from slug path) */
data class CreateCommentHttpRequest(val parentCommentId: UUID? = null, val markdownSource: String)
