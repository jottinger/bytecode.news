/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** Request to modify an existing post's title and content */
data class EditContentRequest(val id: UUID, val title: String, val markdownSource: String)
