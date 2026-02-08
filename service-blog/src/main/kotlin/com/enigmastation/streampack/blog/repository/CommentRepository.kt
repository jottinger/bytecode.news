/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.repository

import com.enigmastation.streampack.blog.entity.Comment
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** Retrieves comments for thread display and user history */
interface CommentRepository : JpaRepository<Comment, UUID> {
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    fun findByPost(postId: UUID): List<Comment>

    @Query(
        "SELECT c FROM Comment c WHERE c.author.id = :authorId AND c.deleted = false ORDER BY c.createdAt DESC"
    )
    fun findByAuthor(authorId: UUID): List<Comment>

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId AND c.deleted = false")
    fun countActiveByPost(postId: UUID): Long
}
