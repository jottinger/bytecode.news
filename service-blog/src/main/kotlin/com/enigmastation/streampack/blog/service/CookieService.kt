/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.service

import com.enigmastation.streampack.core.config.StreampackProperties
import jakarta.servlet.http.Cookie
import org.springframework.stereotype.Service

/** Centralizes creation and clearing of authentication cookies */
@Service
class CookieService(properties: StreampackProperties) {
    private val secure = properties.cookie.secure
    private val jwtMaxAge = (properties.jwt.expirationHours * 3600).toInt()
    private val refreshMaxAge = (properties.refreshToken.days * 86400).toInt()

    /** Creates an httpOnly cookie carrying the JWT access token */
    fun createAccessTokenCookie(jwt: String): Cookie =
        Cookie(ACCESS_TOKEN_COOKIE, jwt).apply {
            isHttpOnly = true
            this.secure = this@CookieService.secure
            path = "/"
            maxAge = jwtMaxAge
            setAttribute("SameSite", "Strict")
        }

    /** Creates an httpOnly cookie carrying the refresh token */
    fun createRefreshTokenCookie(refreshToken: String): Cookie =
        Cookie(REFRESH_TOKEN_COOKIE, refreshToken).apply {
            isHttpOnly = true
            this.secure = this@CookieService.secure
            path = "/"
            maxAge = refreshMaxAge
            setAttribute("SameSite", "Strict")
        }

    /** Creates an expired access token cookie to clear it from the browser */
    fun clearAccessTokenCookie(): Cookie =
        Cookie(ACCESS_TOKEN_COOKIE, "").apply {
            isHttpOnly = true
            this.secure = this@CookieService.secure
            path = "/"
            maxAge = 0
            setAttribute("SameSite", "Strict")
        }

    /** Creates an expired refresh token cookie to clear it from the browser */
    fun clearRefreshTokenCookie(): Cookie =
        Cookie(REFRESH_TOKEN_COOKIE, "").apply {
            isHttpOnly = true
            this.secure = this@CookieService.secure
            path = "/"
            maxAge = 0
            setAttribute("SameSite", "Strict")
        }

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }
}
