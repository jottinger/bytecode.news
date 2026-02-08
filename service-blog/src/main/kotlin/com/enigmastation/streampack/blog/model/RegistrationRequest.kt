/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Public registration request with account credentials */
data class RegistrationRequest(
    val username: String,
    val email: String,
    val displayName: String,
    val password: String,
)
