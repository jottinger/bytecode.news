/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Central configuration for all streampack core services */
@ConfigurationProperties(prefix = "streampack")
data class StreampackProperties(
    val baseUrl: String = "http://localhost:8080",
    val jwt: JwtProperties = JwtProperties(),
    val token: TokenProperties = TokenProperties(),
    val mail: MailProperties = MailProperties(),
) {
    data class JwtProperties(val secret: String = "", val expirationHours: Long = 24)

    data class TokenProperties(
        val emailVerificationHours: Long = 24,
        val passwordResetHours: Long = 1,
    )

    data class MailProperties(val from: String = "noreply@jvm.news")
}
