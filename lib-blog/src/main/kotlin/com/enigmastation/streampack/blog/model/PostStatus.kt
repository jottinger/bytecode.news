/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import io.swagger.v3.oas.annotations.media.Schema

/** Workflow states for blog post lifecycle */
@Schema(description = "Post lifecycle status: DRAFT (awaiting review) or APPROVED (publishable)")
enum class PostStatus {
    DRAFT,
    APPROVED,
}
