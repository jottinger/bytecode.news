/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.model.ContentDetail
import com.enigmastation.streampack.blog.model.ContentListResponse
import com.enigmastation.streampack.blog.model.ContentSummary
import com.enigmastation.streampack.blog.model.FindContentRequest
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Retrieves blog content by slug, ID, or paginated published listing */
@Component
class FindContentOperation(
    private val postRepository: PostRepository,
    private val slugRepository: SlugRepository,
    private val commentRepository: CommentRepository,
    private val postTagRepository: PostTagRepository,
    private val postCategoryRepository: PostCategoryRepository,
) : TypedOperation<FindContentRequest>(FindContentRequest::class) {

    override val priority = 50

    override fun handle(payload: FindContentRequest, message: Message<*>): OperationOutcome {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val user = provenance?.user

        return when (payload) {
            is FindContentRequest.FindBySlug -> findBySlug(payload.path, user)
            is FindContentRequest.FindById -> findById(payload.id, user)
            is FindContentRequest.FindPublished -> findPublished(payload.page, payload.size)
            is FindContentRequest.Search ->
                searchPublished(payload.query, payload.page, payload.size)
            is FindContentRequest.FindByCategory ->
                findByCategory(payload.categoryName, payload.page, payload.size)
            is FindContentRequest.FindByTag ->
                findByTag(payload.tagName, payload.page, payload.size)
            is FindContentRequest.FindPage -> findPage(payload.slug, user)
        }
    }

    private fun findBySlug(path: String, user: UserPrincipal?): OperationResult {
        val slug = slugRepository.resolve(path) ?: return OperationResult.Error("Post not found")

        val post =
            postRepository.findActiveByIdWithAuthor(slug.post.id)
                ?: return OperationResult.Error("Post not found")

        if (!isVisible(post, user)) {
            return OperationResult.Error("Post not found")
        }

        val canonicalSlug = slugRepository.findCanonical(post.id)
        return OperationResult.Success(toDetail(post, canonicalSlug?.path ?: path, user))
    }

    private fun findById(id: java.util.UUID, user: UserPrincipal?): OperationResult {
        val post =
            postRepository.findActiveByIdWithAuthor(id)
                ?: return OperationResult.Error("Post not found")

        if (!isVisible(post, user)) {
            return OperationResult.Error("Post not found")
        }

        val canonicalSlug = slugRepository.findCanonical(post.id)
        return OperationResult.Success(toDetail(post, canonicalSlug?.path ?: "", user))
    }

    private fun findPublished(page: Int, size: Int): OperationResult {
        val now = Instant.now()
        val pageResult = postRepository.findPublished(now, PageRequest.of(page, size))

        val summaries =
            pageResult.content.map { post ->
                val canonicalSlug = slugRepository.findCanonical(post.id)
                ContentSummary(
                    id = post.id,
                    title = post.title,
                    slug = canonicalSlug?.path ?: "",
                    excerpt = post.excerpt,
                    authorDisplayName = post.author?.displayName ?: "Anonymous",
                    publishedAt = post.publishedAt,
                    commentCount = commentRepository.countActiveByPost(post.id).toInt(),
                    tags = tagNamesForPost(post.id),
                    categories = categoryNamesForPost(post.id),
                )
            }

        return OperationResult.Success(
            ContentListResponse(
                posts = summaries,
                page = pageResult.number,
                totalPages = pageResult.totalPages,
                totalCount = pageResult.totalElements,
            )
        )
    }

    private fun searchPublished(query: String, page: Int, size: Int): OperationResult {
        if (query.isBlank()) {
            return OperationResult.Success(
                ContentListResponse(posts = emptyList(), page = 0, totalPages = 0, totalCount = 0)
            )
        }

        val now = Instant.now()
        val pageResult = postRepository.searchPublished(query, now, PageRequest.of(page, size))

        val summaries =
            pageResult.content.map { post ->
                val canonicalSlug = slugRepository.findCanonical(post.id)
                ContentSummary(
                    id = post.id,
                    title = post.title,
                    slug = canonicalSlug?.path ?: "",
                    excerpt = post.excerpt,
                    authorDisplayName = post.author?.displayName ?: "Anonymous",
                    publishedAt = post.publishedAt,
                    commentCount = commentRepository.countActiveByPost(post.id).toInt(),
                    tags = tagNamesForPost(post.id),
                    categories = categoryNamesForPost(post.id),
                )
            }

        return OperationResult.Success(
            ContentListResponse(
                posts = summaries,
                page = pageResult.number,
                totalPages = pageResult.totalPages,
                totalCount = pageResult.totalElements,
            )
        )
    }

    private fun findByCategory(categoryName: String, page: Int, size: Int): OperationResult {
        val now = Instant.now()
        val pageResult =
            postRepository.findByCategory(categoryName, now, PageRequest.of(page, size))

        val summaries =
            pageResult.content.map { post ->
                val canonicalSlug = slugRepository.findCanonical(post.id)
                ContentSummary(
                    id = post.id,
                    title = post.title,
                    slug = canonicalSlug?.path ?: "",
                    excerpt = post.excerpt,
                    authorDisplayName = post.author?.displayName ?: "Anonymous",
                    publishedAt = post.publishedAt,
                    commentCount = commentRepository.countActiveByPost(post.id).toInt(),
                    tags = tagNamesForPost(post.id),
                    categories = categoryNamesForPost(post.id),
                )
            }

        return OperationResult.Success(
            ContentListResponse(
                posts = summaries,
                page = pageResult.number,
                totalPages = pageResult.totalPages,
                totalCount = pageResult.totalElements,
            )
        )
    }

    private fun findByTag(tagName: String, page: Int, size: Int): OperationResult {
        val now = Instant.now()
        val pageResult = postRepository.findByTag(tagName, now, PageRequest.of(page, size))

        val summaries =
            pageResult.content.map { post ->
                val canonicalSlug = slugRepository.findCanonical(post.id)
                ContentSummary(
                    id = post.id,
                    title = post.title,
                    slug = canonicalSlug?.path ?: "",
                    excerpt = post.excerpt,
                    authorDisplayName = post.author?.displayName ?: "Anonymous",
                    publishedAt = post.publishedAt,
                    commentCount = commentRepository.countActiveByPost(post.id).toInt(),
                    tags = tagNamesForPost(post.id),
                    categories = categoryNamesForPost(post.id),
                )
            }

        return OperationResult.Success(
            ContentListResponse(
                posts = summaries,
                page = pageResult.number,
                totalPages = pageResult.totalPages,
                totalCount = pageResult.totalElements,
            )
        )
    }

    private fun findPage(slug: String, user: UserPrincipal?): OperationResult {
        val now = Instant.now()
        val post =
            postRepository.findBySystemCategoryAndSlug(slug, now)
                ?: return OperationResult.Error("Page not found")

        val canonicalSlug = slugRepository.findCanonical(post.id)
        return OperationResult.Success(toDetail(post, canonicalSlug?.path ?: slug, user))
    }

    /** Check visibility rules based on post state and requesting user */
    private fun isVisible(post: Post, user: UserPrincipal?): Boolean {
        val now = Instant.now()
        val isPublished =
            post.status == PostStatus.APPROVED &&
                post.publishedAt != null &&
                !post.publishedAt.isAfter(now)

        if (isPublished) return true

        // Unpublished content: only author or admin can see
        if (user == null) return false
        if (user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN) return true
        if (post.author != null && post.author.id == user.id) return true

        return false
    }

    private fun toDetail(post: Post, slug: String, user: UserPrincipal? = null): ContentDetail {
        val canEdit = user != null && (user.role == Role.ADMIN || user.role == Role.SUPER_ADMIN)

        return ContentDetail(
            id = post.id,
            title = post.title,
            slug = slug,
            renderedHtml = post.renderedHtml,
            excerpt = post.excerpt,
            authorId = post.author?.id,
            authorDisplayName = post.author?.displayName ?: "Anonymous",
            status = post.status,
            publishedAt = post.publishedAt,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            commentCount = commentRepository.countActiveByPost(post.id).toInt(),
            tags = tagNamesForPost(post.id),
            categories = categoryNamesForPost(post.id),
            markdownSource = if (canEdit) post.markdownSource else null,
        )
    }

    private fun tagNamesForPost(postId: UUID): List<String> =
        postTagRepository.findByPost(postId).map { it.tag.name }

    private fun categoryNamesForPost(postId: UUID): List<String> =
        postCategoryRepository.findByPost(postId).map { it.category.name }
}
