/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** HTTP request body for creating a post (separates JSON binding from operation payload) */
data class CreateContentHttpRequest(
    val title: String = "",
    val markdownSource: String = "",
    val tags: List<String>? = emptyList(),
    val categoryIds: List<UUID>? = emptyList(),
)
