/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.operation

import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.blog.repository.TagRepository
import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin operation for browsing, searching, and removing article ideas */
@Component
class IdeasBrowseOperation(
    private val tagRepository: TagRepository,
    private val postTagRepository: PostTagRepository,
    private val postRepository: PostRepository,
) : TypedOperation<String>(String::class) {

    override val priority: Int = 50
    override val addressed: Boolean = true
    override val operationGroup: String = "ideas"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val cmd = payload.compress().lowercase()
        return cmd == "ideas" || cmd.startsWith("ideas ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        requireRole(message, Role.ADMIN)?.let {
            return it
        }

        val compressed = payload.compress()
        val args = compressed.substringAfter("ideas", "").trim()
        val cmd = args.lowercase()

        return when {
            args.isEmpty() -> listIdeas()
            cmd == "search" || cmd.startsWith("search ") -> {
                val term = args.substringAfter("search", "").trim()
                searchIdeas(term)
            }
            cmd.startsWith("remove #") -> {
                val numberStr = cmd.substringAfter("remove #").trim()
                val number = numberStr.toIntOrNull()
                if (number == null) {
                    OperationResult.Error("Invalid idea number. Usage: '{{ref:ideas remove #N}}'")
                } else {
                    removeIdea(number)
                }
            }
            cmd.startsWith("remove ") -> {
                val numberStr = cmd.substringAfter("remove ").trim()
                val number = numberStr.toIntOrNull()
                if (number == null) {
                    OperationResult.Error("Invalid idea number. Usage: '{{ref:ideas remove #N}}'")
                } else {
                    removeIdea(number)
                }
            }
            else ->
                OperationResult.Error(
                    "Unknown ideas command. Use '{{ref:ideas}}' to list, " +
                        "'{{ref:ideas search <term>}}' to search, " +
                        "or '{{ref:ideas remove #N}}' to remove."
                )
        }
    }

    private fun listIdeas(): OperationOutcome {
        val ideas = findIdeaPosts()
        if (ideas.isEmpty()) {
            return OperationResult.Success("No article ideas found.")
        }
        return OperationResult.Success(formatIdeaList(ideas))
    }

    private fun searchIdeas(term: String): OperationOutcome {
        if (term.isBlank()) {
            return OperationResult.Error("Search term is required.")
        }
        val ideas = findIdeaPosts()
        val filtered = ideas.filter { it.title.contains(term, ignoreCase = true) }
        if (filtered.isEmpty()) {
            return OperationResult.Success("No ideas matching \"$term\".")
        }
        return OperationResult.Success(formatIdeaList(filtered))
    }

    private fun removeIdea(number: Int): OperationOutcome {
        val ideas = findIdeaPosts()
        if (number < 1 || number > ideas.size) {
            return OperationResult.Error(
                "Idea #$number not found. There are ${ideas.size} idea${if (ideas.size != 1) "s" else ""}."
            )
        }
        val idea = ideas[number - 1]
        postRepository.save(idea.copy(deleted = true))
        logger.info("Idea soft-deleted: {} ({})", idea.title, idea.id)
        return OperationResult.Success("Removed idea #$number: \"${idea.title}\".")
    }

    /** Finds all draft posts tagged with _idea that are not deleted */
    private fun findIdeaPosts(): List<com.enigmastation.streampack.blog.entity.Post> {
        val tag = tagRepository.findByName("_idea") ?: return emptyList()
        val postTags = postTagRepository.findByTag(tag.id)
        val postIds = postTags.map { it.post.id }.toSet()
        return postRepository
            .findAllById(postIds)
            .filter { it.status == PostStatus.DRAFT && !it.deleted }
            .sortedByDescending { it.createdAt }
    }

    private fun formatIdeaList(ideas: List<com.enigmastation.streampack.blog.entity.Post>): String {
        val header = "Article ideas (${ideas.size}):"
        val lines =
            ideas.mapIndexed { index, post ->
                val authorName = post.author?.displayName ?: "Anonymous"
                "#${index + 1} \"${post.title}\" by $authorName"
            }
        return (listOf(header) + lines).joinToString("\n")
    }
}
