/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to derive a heuristic summary from unsaved post content. */
data class DeriveSummaryRequest(val title: String = "", val markdownSource: String = "")
