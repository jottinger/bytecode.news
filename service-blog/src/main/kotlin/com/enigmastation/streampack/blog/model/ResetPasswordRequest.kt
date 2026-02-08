/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to complete a password reset using a token from email */
data class ResetPasswordRequest(val token: String, val newPassword: String)
