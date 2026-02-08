/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.repository

import com.enigmastation.streampack.blog.entity.Post
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** Queries for blog post retrieval by visibility state */
interface PostRepository : JpaRepository<Post, UUID> {
    @Query(
        "SELECT p FROM Post p WHERE p.status = com.enigmastation.streampack.blog.model.PostStatus.APPROVED AND p.deleted = false AND p.publishedAt <= :now ORDER BY p.publishedAt DESC"
    )
    fun findPublished(now: Instant): List<Post>

    @Query(
        "SELECT p FROM Post p WHERE p.status = com.enigmastation.streampack.blog.model.PostStatus.DRAFT AND p.deleted = false"
    )
    fun findDrafts(): List<Post>

    @Query(
        "SELECT p FROM Post p WHERE p.status = com.enigmastation.streampack.blog.model.PostStatus.APPROVED AND p.deleted = false AND p.publishedAt > :now ORDER BY p.publishedAt ASC"
    )
    fun findScheduled(now: Instant): List<Post>

    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId AND p.deleted = false")
    fun findByAuthor(authorId: UUID): List<Post>

    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deleted = false")
    fun findActiveById(id: UUID): Post?

    /** Paginated published posts for listing pages */
    @Query(
        "SELECT p FROM Post p WHERE p.status = com.enigmastation.streampack.blog.model.PostStatus.APPROVED AND p.deleted = false AND p.publishedAt <= :now ORDER BY p.publishedAt DESC"
    )
    fun findPublished(now: Instant, pageable: Pageable): Page<Post>

    /** Fetch post with author eagerly loaded to avoid LazyInitializationException in DTO mapping */
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.author WHERE p.id = :id AND p.deleted = false")
    fun findActiveByIdWithAuthor(id: UUID): Post?
}
