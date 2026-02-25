/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to verify a one-time passcode and authenticate */
data class OtpVerifyRequest(val email: String, val code: String)
