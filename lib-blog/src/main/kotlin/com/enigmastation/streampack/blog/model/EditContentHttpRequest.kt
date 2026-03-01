/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** HTTP request body for editing a post (id resolved from path) */
data class EditContentHttpRequest(
    val title: String? = "",
    val markdownSource: String? = "",
    val tags: List<String>? = emptyList(),
    val categoryIds: List<UUID>? = emptyList(),
)
