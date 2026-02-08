/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** Sealed hierarchy for content retrieval requests across all protocols */
sealed interface FindContentRequest {
    data class FindBySlug(val path: String) : FindContentRequest

    data class FindById(val id: UUID) : FindContentRequest

    data class FindPublished(val page: Int = 0, val size: Int = 20) : FindContentRequest
}
