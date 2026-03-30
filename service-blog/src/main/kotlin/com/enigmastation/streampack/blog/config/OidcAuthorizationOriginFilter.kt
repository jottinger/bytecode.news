/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Captures the validated initiating frontend origin before the OAuth2 redirect leaves the app. */
@Component
class OidcAuthorizationOriginFilter(private val redirectOriginSupport: OidcRedirectOriginSupport) :
    OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.method != "GET" || !request.requestURI.startsWith("/oauth2/authorization/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        redirectOriginSupport.updateOriginCookie(request, response)
        filterChain.doFilter(request, response)
    }
}
