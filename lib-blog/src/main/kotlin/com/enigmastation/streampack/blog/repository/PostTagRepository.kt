/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.repository

import com.enigmastation.streampack.blog.entity.PostTag
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

/** Manages post-to-tag assignments */
interface PostTagRepository : JpaRepository<PostTag, UUID> {
    @Query("SELECT pt FROM PostTag pt WHERE pt.post.id = :postId")
    fun findByPost(postId: UUID): List<PostTag>

    @Query("SELECT pt FROM PostTag pt WHERE pt.tag.id = :tagId")
    fun findByTag(tagId: UUID): List<PostTag>

    @Modifying
    @Transactional
    @Query("DELETE FROM PostTag pt WHERE pt.post.id = :postId")
    fun deleteByPost(postId: UUID)
}
