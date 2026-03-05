/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.model

import com.enigmastation.streampack.blog.model.CreateContentRequest

/** Per-channel article idea session state, serialized to JSONB via ProvenanceStateService */
data class IdeaSessionState(
    val title: String,
    val contentBlocks: List<String> = emptyList(),
    val submitterName: String,
    val sourceProvenance: String,
    val startedAt: Long,
) {
    /** Builds the markdown body with attribution footer for draft creation */
    fun buildMarkdownBody(): String {
        val body =
            if (contentBlocks.isNotEmpty()) {
                contentBlocks.joinToString("\n\n")
            } else {
                ""
            }
        val attribution = "\n\n---\n*Contributed by $submitterName via $sourceProvenance*"
        return (body + attribution).trimStart()
    }

    /** Converts this session into a CreateContentRequest for dispatch via EventGateway */
    fun toCreateContentRequest(): CreateContentRequest =
        CreateContentRequest(
            title = title,
            markdownSource = buildMarkdownBody(),
            tags = listOf("_idea"),
        )

    companion object {
        const val STATE_KEY = "article-idea"
    }
}
