/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Category
import com.enigmastation.streampack.blog.model.CreateContentRequest
import com.enigmastation.streampack.blog.model.CreateContentResponse
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.blog.repository.TagRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    @Autowired lateinit var tagRepository: TagRepository
    @Autowired lateinit var postTagRepository: PostTagRepository
    @Autowired lateinit var categoryRepository: CategoryRepository

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
    fun `anonymous request creates draft`() {
        val request = CreateContentRequest("Test", "Content")
        val result = eventGateway.process(createMessage(request, null))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals("Anonymous", response.authorDisplayName)
        assertNull(response.authorId)
        assertEquals(PostStatus.DRAFT, response.status)
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

    @Test
    fun `create with tags creates tag entities and associations`() {
        val request =
            CreateContentRequest("Tagged Post", "Content.", tags = listOf("kotlin", "spring"))
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals(listOf("kotlin", "spring"), response.tags)

        val postTags = postTagRepository.findByPost(response.id)
        assertEquals(2, postTags.size)

        assertNotNull(tagRepository.findByName("kotlin"))
        assertNotNull(tagRepository.findByName("spring"))
    }

    @Test
    fun `create with unknown tags auto-creates them`() {
        val request = CreateContentRequest("New Tags", "Content.", tags = listOf("brandnewtag"))
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals(listOf("brandnewtag"), response.tags)

        val tag = tagRepository.findByName("brandnewtag")
        assertNotNull(tag)
        assertEquals("brandnewtag", tag!!.slug)
    }

    @Test
    fun `create with categoryIds creates associations`() {
        val category = categoryRepository.save(Category(name = "JVM", slug = "jvm"))
        val request =
            CreateContentRequest("Categorized Post", "Content.", categoryIds = listOf(category.id))
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals(listOf("JVM"), response.categories)
    }

    @Test
    fun `create with nonexistent categoryId silently skips it`() {
        val request =
            CreateContentRequest("Missing Cat", "Content.", categoryIds = listOf(UUID.randomUUID()))
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertTrue(response.categories.isEmpty())
    }

    @Test
    fun `post in system category gets bare slug without date prefix`() {
        val pagesCategory = categoryRepository.findByName("_pages")!!
        val request =
            CreateContentRequest(
                "About",
                "About this site.",
                categoryIds = listOf(pagesCategory.id),
            )
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertEquals("about", response.slug)
    }

    @Test
    fun `post in normal category gets dated slug`() {
        val category = categoryRepository.save(Category(name = "News", slug = "news"))
        val request =
            CreateContentRequest(
                "Big News",
                "Something happened.",
                categoryIds = listOf(category.id),
            )
        val result = eventGateway.process(createMessage(request, verifiedUser))

        val response = (result as OperationResult.Success).payload as CreateContentResponse
        assertTrue(response.slug.matches(Regex("\\d{4}/\\d{2}/big-news")))
    }
}
