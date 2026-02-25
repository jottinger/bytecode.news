/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Admin request to suspend a user account, freezing login while preserving content attribution */
data class SuspendAccountRequest(val username: String)
