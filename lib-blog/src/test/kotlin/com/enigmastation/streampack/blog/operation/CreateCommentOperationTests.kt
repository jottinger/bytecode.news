/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.model.CommentDetail
import com.enigmastation.streampack.blog.model.CreateCommentRequest
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostRepository
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class CreateCommentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var commentRepository: CommentRepository

    private lateinit var verifiedUser: User
    private lateinit var unverifiedUser: User
    private lateinit var publishedPost: Post

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        verifiedUser =
            userRepository.save(
                User(
                    username = "commenter",
                    email = "commenter@test.com",
                    displayName = "Test Commenter",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        unverifiedUser =
            userRepository.save(
                User(
                    username = "unverified",
                    email = "unverified@test.com",
                    displayName = "Unverified User",
                    emailVerified = false,
                    role = Role.USER,
                )
            )

        publishedPost =
            postRepository.save(
                Post(
                    title = "Test Post",
                    markdownSource = "Post content.",
                    renderedHtml = "<p>Post content.</p>",
                    excerpt = "Post content.",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = verifiedUser,
                    createdAt = now,
                    updatedAt = now,
                )
            )
    }

    private fun createMessage(request: CreateCommentRequest, user: User?) =
        MessageBuilder.withPayload(request)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "comments",
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
    fun `successful top-level comment creation`() {
        val request = CreateCommentRequest(publishedPost.id, null, "This is a comment.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as CommentDetail
        assertEquals(publishedPost.id, detail.postId)
        assertEquals("Test Commenter", detail.authorDisplayName)
        assertNotNull(detail.createdAt)
    }

    @Test
    fun `successful nested comment creation`() {
        val parentComment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "Parent comment.",
                    renderedHtml = "<p>Parent comment.</p>",
                )
            )

        val request = CreateCommentRequest(publishedPost.id, parentComment.id, "This is a reply.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as CommentDetail
        assertEquals(publishedPost.id, detail.postId)

        val savedComment = commentRepository.findById(detail.id).orElse(null)
        assertNotNull(savedComment)
        assertNotNull(savedComment.parentComment)
        assertEquals(parentComment.id, savedComment.parentComment!!.id)
    }

    @Test
    fun `markdown rendered to HTML`() {
        val request = CreateCommentRequest(publishedPost.id, null, "**bold** and *italic*")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val detail = (result as OperationResult.Success).payload as CommentDetail
        assertTrue(detail.renderedHtml.contains("<strong>bold</strong>"))
        assertTrue(detail.renderedHtml.contains("<em>italic</em>"))
    }

    @Test
    fun `unverified email returns error`() {
        val request = CreateCommentRequest(publishedPost.id, null, "A comment.")
        val result = eventGateway.process(createMessage(request, unverifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Email verification required", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated returns error`() {
        val request = CreateCommentRequest(publishedPost.id, null, "A comment.")
        val result = eventGateway.process(createMessage(request, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Authentication required", (result as OperationResult.Error).message)
    }

    @Test
    fun `blank markdownSource returns error`() {
        val request = CreateCommentRequest(publishedPost.id, null, "   ")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Comment content is required", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent post returns error`() {
        val request = CreateCommentRequest(UUID.randomUUID(), null, "A comment.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent parent comment returns error`() {
        val request = CreateCommentRequest(publishedPost.id, UUID.randomUUID(), "A reply.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Parent comment not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `parent comment on different post returns error`() {
        val otherPost =
            postRepository.save(
                Post(
                    title = "Other Post",
                    markdownSource = "Other content.",
                    renderedHtml = "<p>Other content.</p>",
                    excerpt = "Other content.",
                    status = PostStatus.APPROVED,
                    publishedAt = Instant.now().minus(1, ChronoUnit.HOURS),
                    author = verifiedUser,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )
        val commentOnOtherPost =
            commentRepository.save(
                Comment(
                    post = otherPost,
                    author = verifiedUser,
                    markdownSource = "Comment on other post.",
                    renderedHtml = "<p>Comment on other post.</p>",
                )
            )

        val request =
            CreateCommentRequest(publishedPost.id, commentOnOtherPost.id, "Cross-post reply.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(
            "Parent comment belongs to a different post",
            (result as OperationResult.Error).message,
        )
    }
}
