/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.CreateContentRequest
import com.enigmastation.streampack.blog.model.CreateContentResponse
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
class CreateContentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository

    private lateinit var verifiedUser: User
    private lateinit var unverifiedUser: User

    @BeforeEach
    fun setUp() {
        verifiedUser =
            userRepository.save(
                User(
                    username = "writer",
                    email = "writer@test.com",
                    displayName = "Test Writer",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        unverifiedUser =
            userRepository.save(
                User(
                    username = "newuser",
                    email = "newuser@test.com",
                    displayName = "New User",
                    emailVerified = false,
                    role = Role.USER,
                )
            )
    }

    private fun createMessage(request: CreateContentRequest, user: User?) =
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
    fun `successful creation returns DRAFT with generated slug`() {
        val request = CreateContentRequest("Hello World", "This is my first post.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals("Hello World", response.title)
        assertEquals(PostStatus.DRAFT, response.status)
        assertEquals(verifiedUser.id, response.authorId)
        assertEquals("Test Writer", response.authorDisplayName)
        assertNotNull(response.createdAt)
    }

    @Test
    fun `slug format matches year-month-title pattern`() {
        val request = CreateContentRequest("Hello World", "Content here.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertTrue(response.slug.matches(Regex("\\d{4}/\\d{2}/hello-world")))
    }

    @Test
    fun `markdown rendered to HTML in saved post`() {
        val request = CreateContentRequest("Test Post", "# Heading\n\nParagraph text.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        val savedPost = postRepository.findById(response.id).orElse(null)
        assertNotNull(savedPost)
        assertTrue(savedPost.renderedHtml.contains("<h1>Heading</h1>"))
        assertTrue(savedPost.renderedHtml.contains("<p>Paragraph text.</p>"))
    }

    @Test
    fun `excerpt auto-generated`() {
        val request = CreateContentRequest("Test Post", "This is the content of the post.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertNotNull(response.excerpt)
        assertTrue(response.excerpt!!.contains("content of the post"))
    }

    @Test
    fun `unauthenticated request returns error`() {
        val request = CreateContentRequest("Test", "Content")
        val result = eventGateway.process(createMessage(request, null))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Authentication required", (result as OperationResult.Error).message)
    }

    @Test
    fun `unverified email returns error`() {
        val request = CreateContentRequest("Test", "Content")
        val result = eventGateway.process(createMessage(request, unverifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Email verification required", (result as OperationResult.Error).message)
    }

    @Test
    fun `blank title returns error`() {
        val request = CreateContentRequest("", "Content")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Title is required", (result as OperationResult.Error).message)
    }

    @Test
    fun `blank markdownSource returns error`() {
        val request = CreateContentRequest("Title", "")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Content is required", (result as OperationResult.Error).message)
    }

    @Test
    fun `slug saved to repository`() {
        val request = CreateContentRequest("Slug Test", "Content for slug test.")
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        val slug = slugRepository.findCanonical(response.id)
        assertNotNull(slug)
        assertEquals(response.slug, slug!!.path)
        assertTrue(slug.canonical)
    }
}
