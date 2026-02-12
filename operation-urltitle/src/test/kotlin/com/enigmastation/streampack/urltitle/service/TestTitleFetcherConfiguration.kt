/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.urltitle.service

import com.enigmastation.streampack.core.service.TitleFetcher
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Provides a predictable TitleFetcher for tests that returns titles based on URL */
@TestConfiguration
class TestTitleFetcherConfiguration {

    @Bean @Primary fun testTitleFetcher(): TitleFetcher = TestTitleFetcher()
}

/** Returns controlled titles keyed by URL, allowing tests to verify summary formatting */
class TestTitleFetcher : TitleFetcher {
    private val titles = mutableMapOf<String, String>()

    fun setTitle(url: String, title: String) {
        titles[url] = title
    }

    fun clear() {
        titles.clear()
    }

    override fun fetchTitle(url: String): String? = titles[url]
}
