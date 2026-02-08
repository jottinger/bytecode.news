/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Result of an admin-initiated password reset with the generated temporary password */
data class PasswordResetResponse(val username: String, val temporaryPassword: String)
