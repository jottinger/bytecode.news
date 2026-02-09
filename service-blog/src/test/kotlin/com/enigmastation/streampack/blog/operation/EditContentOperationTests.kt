/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.ContentDetail
import com.enigmastation.streampack.blog.model.EditContentRequest
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class EditContentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository

    private lateinit var author: User
    private lateinit var admin: User
    private lateinit var otherUser: User
    private lateinit var draftPost: Post
    private lateinit var approvedPost: Post

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        author =
            userRepository.save(
                User(
                    username = "editauthor",
                    email = "editauthor@test.com",
                    displayName = "Edit Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        admin =
            userRepository.save(
                User(
                    username = "editadmin",
                    email = "editadmin@test.com",
                    displayName = "Edit Admin",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )
        otherUser =
            userRepository.save(
                User(
                    username = "editother",
                    email = "editother@test.com",
                    displayName = "Edit Other",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        draftPost =
            postRepository.save(
                Post(
                    title = "Original Draft Title",
                    markdownSource = "Original draft content.",
                    renderedHtml = "<p>Original draft content.</p>",
                    excerpt = "Original draft content.",
                    status = PostStatus.DRAFT,
                    author = author,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        slugRepository.save(
            Slug(path = "2026/02/original-draft-title", post = draftPost, canonical = true)
        )

        approvedPost =
            postRepository.save(
                Post(
                    title = "Approved Post",
                    markdownSource = "Approved content.",
                    renderedHtml = "<p>Approved content.</p>",
                    excerpt = "Approved content.",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        slugRepository.save(
            Slug(path = "2026/02/approved-post", post = approvedPost, canonical = true)
        )
    }

    private fun editMessage(request: EditContentRequest, user: User?) =
        MessageBuilder.withPayload(request)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "posts",
                    user =
                        user?.let {
                            UserPrincipal(
                                id = it.id,
                                username = it.username,
                                displayName = it.displayName,
                                role = it.role,
                            )
                        },
                ),
            )
            .build()

    @Test
    fun `author edits own draft successfully`() {
        val request =
            EditContentRequest(draftPost.id, "Updated Title", "# Updated\n\nNew content here.")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Updated Title", detail.title)
        assertTrue(detail.renderedHtml.contains("<h1>Updated</h1>"))
        assertTrue(detail.renderedHtml.contains("<p>New content here.</p>"))
        assertTrue(detail.excerpt!!.contains("New content here"))
    }

    @Test
    fun `admin edits any post including approved`() {
        val request =
            EditContentRequest(approvedPost.id, "Admin Edit", "Admin updated this content.")
        val result = eventGateway.process(editMessage(request, admin))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Admin Edit", detail.title)
        assertEquals(PostStatus.APPROVED, detail.status)
    }

    @Test
    fun `non-author user cannot edit another's draft`() {
        val request = EditContentRequest(draftPost.id, "Hijacked", "I changed your post.")
        val result = eventGateway.process(editMessage(request, otherUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authorized to edit this post", (result as OperationResult.Error).message)
    }

    @Test
    fun `non-admin user cannot edit approved post`() {
        val request =
            EditContentRequest(approvedPost.id, "My Edit", "Trying to edit approved post.")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authorized to edit this post", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val request = EditContentRequest(draftPost.id, "Anon Edit", "Anonymous attempt.")
        val result = eventGateway.process(editMessage(request, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Authentication required", (result as OperationResult.Error).message)
    }

    @Test
    fun `blank title returns error`() {
        val request = EditContentRequest(draftPost.id, "", "Content here.")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Title is required", (result as OperationResult.Error).message)
    }

    @Test
    fun `blank markdownSource returns error`() {
        val request = EditContentRequest(draftPost.id, "Title", "")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Content is required", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent post returns error`() {
        val request = EditContentRequest(UUID.randomUUID(), "Title", "Content")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `slug unchanged after edit`() {
        val request = EditContentRequest(draftPost.id, "Completely Different Title", "New content.")
        val result = eventGateway.process(editMessage(request, author))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("2026/02/original-draft-title", detail.slug)
    }
}
