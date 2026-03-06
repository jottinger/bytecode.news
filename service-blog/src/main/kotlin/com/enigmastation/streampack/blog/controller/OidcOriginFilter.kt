/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Captures the frontend origin from OIDC authorization requests so the success handler can redirect
 * back to the correct frontend. Validates the origin against configured CORS allowed origins.
 */
class OidcOriginFilter(private val corsConfigurationSource: CorsConfigurationSource) :
    OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(OidcOriginFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val origin = request.getParameter("origin")
        if (origin != null && request.requestURI.startsWith("/oauth2/authorization/")) {
            val corsConfig = corsConfigurationSource.getCorsConfiguration(request)
            val allowedOrigins = corsConfig?.allowedOrigins ?: emptyList()

            if (allowedOrigins.any { origin.equals(it, ignoreCase = true) }) {
                val cookie = Cookie(COOKIE_NAME, origin)
                cookie.maxAge = 600
                cookie.isHttpOnly = true
                cookie.path = "/"
                cookie.secure = true
                response.addCookie(cookie)
                logger.debug("Set OIDC origin cookie for {}", origin)
            } else {
                logger.warn("Rejected OIDC origin not in CORS allowed origins: {}", origin)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val COOKIE_NAME = "nevet_oidc_origin"
    }
}
