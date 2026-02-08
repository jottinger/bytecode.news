/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to initiate a user-facing password reset via email */
data class ForgotPasswordRequest(val email: String)
