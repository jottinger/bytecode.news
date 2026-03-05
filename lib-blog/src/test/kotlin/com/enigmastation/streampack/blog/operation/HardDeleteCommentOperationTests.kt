/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.model.ContentOperationConfirmation
import com.enigmastation.streampack.blog.model.HardDeleteCommentRequest
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
import jakarta.persistence.EntityManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class HardDeleteCommentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var commentRepository: CommentRepository
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var author: User
    private lateinit var admin: User
    private lateinit var regularUser: User
    private lateinit var publishedPost: Post
    private lateinit var commentToRemove: Comment

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        author =
            userRepository.save(
                User(
                    username = "hdauthor",
                    email = "hdauthor@test.com",
                    displayName = "HD Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        admin =
            userRepository.save(
                User(
                    username = "hdadmin",
                    email = "hdadmin@test.com",
                    displayName = "HD Admin",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )
        regularUser =
            userRepository.save(
                User(
                    username = "hdregular",
                    email = "hdregular@test.com",
                    displayName = "HD Regular",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        publishedPost =
            postRepository.save(
                Post(
                    title = "HD Test Post",
                    markdownSource = "Post for hard delete tests.",
                    renderedHtml = "<p>Post for hard delete tests.</p>",
                    excerpt = "Post for hard delete tests.",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                    createdAt = now,
                    updatedAt = now,
                )
            )

        commentToRemove =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = author,
                    markdownSource = "Comment to remove.",
                    renderedHtml = "<p>Comment to remove.</p>",
                    createdAt = now,
                    updatedAt = now,
                )
            )
    }

    private fun hardDeleteMessage(request: HardDeleteCommentRequest, user: User?) =
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
    fun `admin hard-deletes comment`() {
        val request = HardDeleteCommentRequest(commentToRemove.id)
        val result = eventGateway.process(hardDeleteMessage(request, admin))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val confirmation =
            (result as OperationResult.Success).payload as ContentOperationConfirmation
        assertEquals(commentToRemove.id, confirmation.id)
        assertEquals("Comment permanently removed", confirmation.message)

        assertFalse(commentRepository.findById(commentToRemove.id).isPresent)
    }

    @Test
    fun `child comments cascaded on hard delete`() {
        val childComment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = author,
                    parentComment = commentToRemove,
                    markdownSource = "Child comment.",
                    renderedHtml = "<p>Child comment.</p>",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )

        val request = HardDeleteCommentRequest(commentToRemove.id)
        eventGateway.process(hardDeleteMessage(request, admin))
        entityManager.clear()

        assertFalse(commentRepository.findById(commentToRemove.id).isPresent)
        assertFalse(commentRepository.findById(childComment.id).isPresent)
    }

    @Test
    fun `non-admin cannot hard-delete`() {
        val request = HardDeleteCommentRequest(commentToRemove.id)
        val result = eventGateway.process(hardDeleteMessage(request, regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(
            "Insufficient privileges: requires ADMIN",
            (result as OperationResult.Error).message,
        )
    }

    @Test
    fun `unauthenticated returns error`() {
        val request = HardDeleteCommentRequest(commentToRemove.id)
        val result = eventGateway.process(hardDeleteMessage(request, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(
            "Insufficient privileges: requires ADMIN",
            (result as OperationResult.Error).message,
        )
    }

    @Test
    fun `nonexistent comment returns error`() {
        val request = HardDeleteCommentRequest(UUID.randomUUID())
        val result = eventGateway.process(hardDeleteMessage(request, admin))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Comment not found", (result as OperationResult.Error).message)
    }
}
