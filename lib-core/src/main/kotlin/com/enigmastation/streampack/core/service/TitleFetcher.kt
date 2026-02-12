/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

/** Abstraction for retrieving the HTML title of a URL */
interface TitleFetcher {
    /** Returns the page title for the given URL, or null on any failure */
    fun fetchTitle(url: String): String?
}
