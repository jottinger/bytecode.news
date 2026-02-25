/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.model.PurgeErasedContentRequest
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.model.UserStatus
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.UserRegistrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

/** Integration tests for admin bulk content purge of erased user sentinels */
@SpringBootTest
@Transactional
class PurgeErasedContentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRegistrationService: UserRegistrationService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var commentRepository: CommentRepository

    private lateinit var adminUser: UserPrincipal
    private lateinit var regularUser: UserPrincipal
    private lateinit var sentinel: User

    @BeforeEach
    fun setUp() {
        adminUser =
            userRegistrationService.register(
                username = "adminuser",
                email = "admin@example.com",
                displayName = "Admin User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "admin@example.com",
                role = Role.ADMIN,
            )
        regularUser =
            userRegistrationService.register(
                username = "regularuser",
                email = "regular@example.com",
                displayName = "Regular User",
                protocol = Protocol.HTTP,
                serviceId = "blog-service",
                externalIdentifier = "regular@example.com",
            )

        // Create an erased sentinel with content
        sentinel =
            userRepository.saveAndFlush(
                User(
                    username = "erased-abc12345",
                    email = "",
                    displayName = "[deleted]",
                    status = UserStatus.ERASED,
                    role = Role.GUEST,
                )
            )
    }

    private fun purgeMessage(sentinelUserId: java.util.UUID, asUser: UserPrincipal?) =
        MessageBuilder.withPayload(PurgeErasedContentRequest(sentinelUserId))
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "admin/users/purge",
                    user = asUser,
                ),
            )
            .build()

    @Test
    fun `admin can purge erased sentinel content`() {
        val post =
            postRepository.saveAndFlush(
                Post(
                    title = "Toxic Post",
                    markdownSource = "bad content",
                    renderedHtml = "<p>bad content</p>",
                    author = sentinel,
                )
            )
        commentRepository.saveAndFlush(
            Comment(
                post = post,
                author = sentinel,
                markdownSource = "bad comment",
                renderedHtml = "<p>bad comment</p>",
            )
        )

        val result = eventGateway.process(purgeMessage(sentinel.id, adminUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("Content purged", (result as OperationResult.Success).payload)

        // Content and sentinel are gone
        assertTrue(postRepository.findById(post.id).isEmpty)
        assertTrue(commentRepository.findByPost(post.id).isEmpty())
        assertNull(userRepository.findByUsername("erased-abc12345"))
    }

    @Test
    fun `cannot purge non-erased user`() {
        val activeUser = userRepository.findByUsername("regularuser")!!
        val result = eventGateway.process(purgeMessage(activeUser.id, adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(
            "Target user is not an erased sentinel",
            (result as OperationResult.Error).message,
        )
    }

    @Test
    fun `non-admin cannot purge`() {
        val result = eventGateway.process(purgeMessage(sentinel.id, regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Insufficient privileges", (result as OperationResult.Error).message)
    }

    @Test
    fun `unauthenticated request returns error`() {
        val result = eventGateway.process(purgeMessage(sentinel.id, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Not authenticated", (result as OperationResult.Error).message)
    }
}
