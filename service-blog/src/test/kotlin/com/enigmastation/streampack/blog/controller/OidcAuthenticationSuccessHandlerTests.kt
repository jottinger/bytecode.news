/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.OidcRedirectOriginSupport
import com.enigmastation.streampack.blog.model.LoginResponse
import com.enigmastation.streampack.blog.service.UserConvergenceService
import com.enigmastation.streampack.core.config.StreampackProperties
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import jakarta.servlet.http.Cookie
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority

/** Verifies the OIDC success handler redirects to frontendUrl when set, or baseUrl as fallback */
class OidcAuthenticationSuccessHandlerTests {

    private val stubResponse =
        LoginResponse(
            token = "test-jwt-token",
            principal =
                UserPrincipal(
                    id = UUID.randomUUID(),
                    username = "testuser",
                    displayName = "Test User",
                    role = Role.USER,
                ),
        )

    private fun mockConvergenceService(): UserConvergenceService {
        val service = mock(UserConvergenceService::class.java)
        `when`(service.converge("test@example.com", "Test User")).thenReturn(stubResponse)
        return service
    }

    private fun oidcAuthentication(email: String): Authentication {
        val idToken =
            OidcIdToken.withTokenValue("id-token")
                .claim("sub", "12345")
                .claim("email", email)
                .claim("name", "Test User")
                .build()
        val user = DefaultOidcUser(listOf(OidcUserAuthority(idToken)), idToken)
        return object : Authentication {
            override fun getName() = email

            override fun getAuthorities() = user.authorities

            override fun getCredentials() = null

            override fun getDetails() = null

            override fun getPrincipal() = user

            override fun isAuthenticated() = true

            override fun setAuthenticated(isAuthenticated: Boolean) {}
        }
    }

    private fun redirectSupport(
        baseUrl: String = "https://rest.bytecode.news",
        frontendUrl: String = "",
        corsOrigins: String = "https://bytecode.news,https://nextjs.bytecode.news",
    ): OidcRedirectOriginSupport {
        return OidcRedirectOriginSupport(
            StreampackProperties(baseUrl = baseUrl, frontendUrl = frontendUrl),
            corsOrigins,
        )
    }

    @Test
    fun `redirects to frontendUrl when set`() {
        val handler =
            OidcAuthenticationSuccessHandler(
                mockConvergenceService(),
                redirectSupport(frontendUrl = "https://bytecode.news"),
            )

        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(
            MockHttpServletRequest(),
            response,
            oidcAuthentication("test@example.com"),
        )

        assertTrue(
            response.redirectedUrl!!.startsWith("https://bytecode.news/auth/callback#token=")
        )
    }

    @Test
    fun `falls back to baseUrl when frontendUrl is empty`() {
        val handler =
            OidcAuthenticationSuccessHandler(
                mockConvergenceService(),
                redirectSupport(baseUrl = "https://rest.bytecode.news"),
            )

        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(
            MockHttpServletRequest(),
            response,
            oidcAuthentication("test@example.com"),
        )

        assertTrue(
            response.redirectedUrl!!.startsWith("https://rest.bytecode.news/auth/callback#token=")
        )
    }

    @Test
    fun `redirects to validated origin from cookie when present`() {
        val handler =
            OidcAuthenticationSuccessHandler(
                mockConvergenceService(),
                redirectSupport(frontendUrl = "https://bytecode.news"),
            )

        val request =
            MockHttpServletRequest().apply {
                setCookies(
                    Cookie(OidcRedirectOriginSupport.COOKIE_NAME, "https://nextjs.bytecode.news")
                )
            }
        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, oidcAuthentication("test@example.com"))

        assertTrue(
            response.redirectedUrl!!.startsWith("https://nextjs.bytecode.news/auth/callback#token=")
        )
    }

    @Test
    fun `falls back when origin cookie is not allowlisted`() {
        val handler =
            OidcAuthenticationSuccessHandler(
                mockConvergenceService(),
                redirectSupport(frontendUrl = "https://bytecode.news"),
            )

        val request =
            MockHttpServletRequest().apply {
                setCookies(Cookie(OidcRedirectOriginSupport.COOKIE_NAME, "https://evil.example"))
            }
        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, oidcAuthentication("test@example.com"))

        assertTrue(
            response.redirectedUrl!!.startsWith("https://bytecode.news/auth/callback#token=")
        )
    }

    @Test
    fun `normalizes trailing slash in fallback url`() {
        val handler =
            OidcAuthenticationSuccessHandler(
                mockConvergenceService(),
                redirectSupport(baseUrl = "https://api.bytecode.news/"),
            )

        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(
            MockHttpServletRequest(),
            response,
            oidcAuthentication("test@example.com"),
        )

        assertTrue(
            response.redirectedUrl!!.startsWith("https://api.bytecode.news/auth/callback#token=")
        )
    }
}
