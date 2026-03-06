/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.repository

import com.enigmastation.streampack.blog.entity.PostCategory
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

/** Manages post-to-category assignments */
interface PostCategoryRepository : JpaRepository<PostCategory, UUID> {
    @Query("SELECT pc FROM PostCategory pc WHERE pc.post.id = :postId")
    fun findByPost(postId: UUID): List<PostCategory>

    @Query("SELECT pc FROM PostCategory pc WHERE pc.category.id = :categoryId")
    fun findByCategory(categoryId: UUID): List<PostCategory>

    @Modifying
    @Transactional
    @Query("DELETE FROM PostCategory pc WHERE pc.post.id = :postId")
    fun deleteByPost(postId: UUID)
}
