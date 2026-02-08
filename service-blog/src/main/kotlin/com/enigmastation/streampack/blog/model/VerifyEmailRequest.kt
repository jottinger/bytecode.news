/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to verify a user's email address via token */
data class VerifyEmailRequest(val token: String)
