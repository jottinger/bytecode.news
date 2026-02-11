/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.CreateContentRequest
import com.enigmastation.streampack.blog.model.CreateContentResponse
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import com.enigmastation.streampack.blog.service.SlugGenerationService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Creates a new blog post draft from a markdown submission */
@Component
class CreateContentOperation(
    private val postRepository: PostRepository,
    private val slugRepository: SlugRepository,
    private val userRepository: UserRepository,
    private val markdownRenderingService: MarkdownRenderingService,
    private val slugGenerationService: SlugGenerationService,
) : TypedOperation<CreateContentRequest>(CreateContentRequest::class) {

    override fun handle(payload: CreateContentRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        val user =
            userRepository.findActiveById(principal.id)
                ?: return OperationResult.Error("User not found")

        if (!user.emailVerified) {
            return OperationResult.Error("Email verification required")
        }

        if (payload.title.isBlank()) {
            return OperationResult.Error("Title is required")
        }
        if (payload.markdownSource.isBlank()) {
            return OperationResult.Error("Content is required")
        }

        val renderedHtml = markdownRenderingService.render(payload.markdownSource)
        val excerpt = markdownRenderingService.excerpt(payload.markdownSource)
        val now = java.time.Instant.now()

        val post =
            postRepository.save(
                Post(
                    title = payload.title,
                    markdownSource = payload.markdownSource,
                    renderedHtml = renderedHtml,
                    excerpt = excerpt,
                    status = PostStatus.DRAFT,
                    author = user,
                    createdAt = now,
                    updatedAt = now,
                )
            )

        val slugPath = slugGenerationService.generateSlug(payload.title, now)
        slugRepository.save(Slug(path = slugPath, post = post, canonical = true))

        logger.info("Post created: {} with slug {}", post.id, slugPath)

        return OperationResult.Success(
            CreateContentResponse(
                id = post.id,
                title = post.title,
                slug = slugPath,
                excerpt = post.excerpt,
                status = post.status,
                authorId = user.id,
                authorDisplayName = user.displayName,
                createdAt = post.createdAt,
            )
        )
    }
}
