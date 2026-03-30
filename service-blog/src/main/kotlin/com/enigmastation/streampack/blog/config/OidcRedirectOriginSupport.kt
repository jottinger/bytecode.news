/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.config

import com.enigmastation.streampack.core.config.StreampackProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * Tracks the initiating frontend origin across the OAuth2 redirect flow.
 *
 * The login trigger may come from one of several allowed frontend origins. That origin is stored in
 * a short-lived cookie and revalidated before the JWT redirect so we do not create an open redirect
 * that leaks tokens to arbitrary callers.
 */
@Component
class OidcRedirectOriginSupport(
    properties: StreampackProperties,
    @Value("\${CORS_ORIGINS:http://localhost:3000,http://localhost:3003,https://bytecode.news}")
    corsOrigins: String,
) {
    private val logger = LoggerFactory.getLogger(OidcRedirectOriginSupport::class.java)
    private val defaultRedirectBase =
        normalizeBaseUrl(properties.frontendUrl.ifEmpty { properties.baseUrl })
    private val allowedOrigins: Set<String> =
        buildSet {
                corsOrigins.split(",").mapTo(this) { it.trim() }
                add(properties.frontendUrl.trim())
                add(properties.baseUrl.trim())
            }
            .mapNotNull(::normalizeOrigin)
            .toSet()

    fun updateOriginCookie(request: HttpServletRequest, response: HttpServletResponse) {
        val rawOrigin = request.getParameter("origin")?.trim()
        val normalizedOrigin = rawOrigin?.let(::normalizeOrigin)
        if (normalizedOrigin != null && normalizedOrigin in allowedOrigins) {
            response.addHeader(
                "Set-Cookie",
                buildCookie(normalizedOrigin, request, COOKIE_TTL_SECONDS),
            )
            return
        }

        if (!rawOrigin.isNullOrBlank()) {
            logger.warn("Ignoring disallowed OIDC origin '{}'", rawOrigin)
        }
        clearOriginCookie(request, response)
    }

    fun resolveRedirectBase(request: HttpServletRequest): String {
        val cookieOrigin =
            request.cookies?.firstOrNull { it.name == COOKIE_NAME }?.value?.let(::normalizeOrigin)

        if (cookieOrigin != null && cookieOrigin in allowedOrigins) {
            return cookieOrigin
        }
        return defaultRedirectBase
    }

    fun clearOriginCookie(request: HttpServletRequest, response: HttpServletResponse) {
        val shouldWriteSecureCookie =
            request.isSecure ||
                request
                    .getHeader("X-Forwarded-Proto")
                    ?.substringBefore(',')
                    ?.trim()
                    ?.equals("https", ignoreCase = true) == true
        response.addHeader(
            "Set-Cookie",
            ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .secure(shouldWriteSecureCookie)
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString(),
        )
    }

    private fun buildCookie(
        value: String,
        request: HttpServletRequest,
        maxAgeSeconds: Long,
    ): String {
        val secure =
            request.isSecure ||
                request
                    .getHeader("X-Forwarded-Proto")
                    ?.substringBefore(',')
                    ?.trim()
                    ?.equals("https", ignoreCase = true) == true
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .sameSite("Lax")
            .secure(secure)
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
            .toString()
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        val parsed = runCatching { java.net.URI(trimmed) }.getOrNull()
        if (parsed?.scheme.isNullOrBlank() || parsed?.host.isNullOrBlank()) {
            return trimmed
        }
        val scheme = parsed!!.scheme.lowercase()
        val host = parsed.host.lowercase()
        val port =
            when {
                parsed.port == -1 -> ""
                parsed.port == 80 && scheme == "http" -> ""
                parsed.port == 443 && scheme == "https" -> ""
                else -> ":${parsed.port}"
            }
        val path = parsed.path?.takeIf { it.isNotBlank() && it != "/" }?.removeSuffix("/") ?: ""
        return "$scheme://$host$port$path"
    }

    private fun normalizeOrigin(raw: String): String? {
        val parsed = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") {
            return null
        }
        val host = parsed.host?.lowercase() ?: return null
        if (!parsed.userInfo.isNullOrBlank()) {
            return null
        }
        if (!parsed.query.isNullOrBlank() || !parsed.fragment.isNullOrBlank()) {
            return null
        }
        if (!parsed.path.isNullOrBlank() && parsed.path != "/") {
            return null
        }
        val port =
            when {
                parsed.port == -1 -> ""
                parsed.port == 80 && scheme == "http" -> ""
                parsed.port == 443 && scheme == "https" -> ""
                else -> ":${parsed.port}"
            }
        return "$scheme://$host$port"
    }

    companion object {
        const val COOKIE_NAME = "streampack-oidc-origin"
        private const val COOKIE_TTL_SECONDS = 300L
    }
}
