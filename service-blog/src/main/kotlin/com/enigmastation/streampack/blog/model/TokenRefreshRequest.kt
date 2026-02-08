/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to issue a fresh JWT from an existing valid token */
data class TokenRefreshRequest(val token: String)
