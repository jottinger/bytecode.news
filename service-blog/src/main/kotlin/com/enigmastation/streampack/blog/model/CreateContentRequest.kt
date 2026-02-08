/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to create a new blog post draft */
data class CreateContentRequest(val title: String, val markdownSource: String)
