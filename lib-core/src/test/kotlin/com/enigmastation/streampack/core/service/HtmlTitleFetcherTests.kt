/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HtmlTitleFetcherTests {

    private fun fetcherWith(html: String?) =
        HtmlTitleFetcher(
            object : PageFetcher {
                override fun fetch(url: String): String? = html
            }
        )

    @Test
    fun `extracts title tag`() {
        val html = "<html><head><title>Hello World</title></head><body></body></html>"
        assertEquals("Hello World", fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `falls back to og title when title tag is empty`() {
        val html =
            """<html><head><title></title>
            <meta property="og:title" content="OG Title Here">
            </head><body></body></html>"""
        assertEquals("OG Title Here", fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `falls back to twitter title when title and og title are absent`() {
        val html =
            """<html><head>
            <meta name="twitter:title" content="Tweet Title">
            </head><body></body></html>"""
        assertEquals("Tweet Title", fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `prefers og title over title tag`() {
        val html =
            """<html><head><title>Site Name</title>
            <meta property="og:title" content="Actual Article Title">
            </head><body></body></html>"""
        assertEquals("Actual Article Title", fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `og title wins over useless title tag like YouTube`() {
        val html =
            """<html><head><title>- YouTube</title>
            <meta property="og:title" content="Top 5 Hollywood DISASTERS of 2025">
            </head><body></body></html>"""
        assertEquals(
            "Top 5 Hollywood DISASTERS of 2025",
            fetcherWith(html).fetchTitle("http://example.com"),
        )
    }

    @Test
    fun `prefers og title over twitter title`() {
        val html =
            """<html><head>
            <meta property="og:title" content="OG Title">
            <meta name="twitter:title" content="Tweet Title">
            </head><body></body></html>"""
        assertEquals("OG Title", fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `returns null when no title found anywhere`() {
        val html = "<html><head></head><body>Just content</body></html>"
        assertNull(fetcherWith(html).fetchTitle("http://example.com"))
    }

    @Test
    fun `returns null when page fetch fails`() {
        assertNull(fetcherWith(null).fetchTitle("http://example.com"))
    }
}
