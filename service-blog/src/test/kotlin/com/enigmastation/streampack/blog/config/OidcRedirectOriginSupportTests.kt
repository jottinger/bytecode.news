/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.config

import com.enigmastation.streampack.core.config.StreampackProperties
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class OidcRedirectOriginSupportTests {

    private val support =
        OidcRedirectOriginSupport(
            StreampackProperties(
                baseUrl = "https://api.bytecode.news",
                frontendUrl = "https://bytecode.news",
            ),
            "https://bytecode.news,https://nextjs.bytecode.news",
        )

    @Test
    fun `stores allowlisted origin in cookie at oauth start`() {
        val filter = OidcAuthorizationOriginFilter(support)
        val request =
            MockHttpServletRequest("GET", "/oauth2/authorization/github").apply {
                setParameter("origin", "https://nextjs.bytecode.news")
                addHeader("X-Forwarded-Proto", "https")
            }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, mock(FilterChain::class.java))

        val setCookie = response.getHeader("Set-Cookie") ?: ""
        assertTrue(
            setCookie.contains(
                "${OidcRedirectOriginSupport.COOKIE_NAME}=https://nextjs.bytecode.news"
            )
        )
        assertTrue(setCookie.contains("HttpOnly"))
    }

    @Test
    fun `clears cookie when origin is not allowlisted`() {
        val request =
            MockHttpServletRequest().apply { setParameter("origin", "https://evil.example") }
        val response = MockHttpServletResponse()

        support.updateOriginCookie(request, response)

        val setCookie = response.getHeader("Set-Cookie") ?: ""
        assertTrue(setCookie.contains("${OidcRedirectOriginSupport.COOKIE_NAME}="))
        assertTrue(setCookie.contains("Max-Age=0"))
    }

    @Test
    fun `resolves configured frontend as default redirect base`() {
        val request = MockHttpServletRequest()
        assertEquals("https://bytecode.news", support.resolveRedirectBase(request))
    }
}
