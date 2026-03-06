/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.service.UserConvergenceService
import com.enigmastation.streampack.core.config.StreampackProperties
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Handles successful OIDC/OAuth2 authentication by converging the external identity to a local user
 * and redirecting to the frontend with a JWT in the URL fragment.
 *
 * Reads the origin cookie set by OidcOriginFilter to determine which frontend to redirect to. Falls
 * back to the configured frontendUrl (or baseUrl) when no cookie is present.
 */
@Component
class OidcAuthenticationSuccessHandler(
    private val userConvergenceService: UserConvergenceService,
    properties: StreampackProperties,
) : AuthenticationSuccessHandler {
    private val logger = LoggerFactory.getLogger(OidcAuthenticationSuccessHandler::class.java)
    private val defaultFrontendUrl = properties.frontendUrl.ifEmpty { properties.baseUrl }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val (email, displayName) = extractUserInfo(authentication)

        logger.info("OIDC authentication succeeded for {}", email)
        val loginResponse = userConvergenceService.converge(email, displayName)

        val targetUrl = resolveTargetUrl(request, response)

        /* Deliver JWT via URL fragment so it is not sent to the server in subsequent requests */
        response.sendRedirect("$targetUrl/auth/callback#token=${loginResponse.token}")
    }

    /** Reads the origin cookie if present, clears it, and returns the target frontend URL */
    private fun resolveTargetUrl(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val cookie =
            request.cookies?.firstOrNull { it.name == OidcOriginFilter.COOKIE_NAME }
                ?: return defaultFrontendUrl

        val origin = cookie.value

        // Delete the cookie after reading
        val deleteCookie = Cookie(OidcOriginFilter.COOKIE_NAME, "")
        deleteCookie.maxAge = 0
        deleteCookie.isHttpOnly = true
        deleteCookie.path = "/"
        deleteCookie.secure = true
        response.addCookie(deleteCookie)

        logger.debug("Using OIDC origin cookie for redirect: {}", origin)
        return origin
    }

    /** Extracts email and display name from either an OIDC or plain OAuth2 principal */
    private fun extractUserInfo(authentication: Authentication): Pair<String, String?> {
        val principal = authentication.principal

        if (principal is OidcUser) {
            val email =
                principal.email
                    ?: throw IllegalStateException("OIDC provider did not return an email")
            val displayName = principal.fullName ?: principal.preferredUsername
            return email to displayName
        }

        if (principal is OAuth2User) {
            /* GitHub returns email in the attributes, not as a standard OIDC claim */
            val email =
                principal.getAttribute<String>("email")
                    ?: throw IllegalStateException("OAuth2 provider did not return an email")
            val displayName = principal.getAttribute<String>("name") ?: principal.name
            return email to displayName
        }

        throw IllegalStateException("Unexpected principal type: ${principal?.javaClass?.name}")
    }
}
