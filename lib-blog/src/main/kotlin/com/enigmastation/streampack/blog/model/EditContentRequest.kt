/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import java.time.Instant
import java.util.*

/** Request to modify an existing post's title and content */
data class EditContentRequest(
    val id: UUID,
    val title: String?,
    val markdownSource: String?,
    val tags: List<String>? = emptyList(),
    val categoryIds: List<UUID>? = emptyList(),
) {
    fun applyTo(post: Post, markdownRenderingService: MarkdownRenderingService): Post {
        val resolvedTitle = title ?: post.title
        val resolvedMarkdown = markdownSource ?: post.markdownSource
        val excerpt =
            markdownRenderingService.excerpt(resolvedMarkdown).ifBlank { resolvedTitle.trim() }
        return post.copy(
            title = resolvedTitle,
            markdownSource = resolvedMarkdown,
            renderedHtml = markdownRenderingService.render(resolvedMarkdown),
            excerpt = excerpt,
            updatedAt = Instant.now(),
        )
    }
}
