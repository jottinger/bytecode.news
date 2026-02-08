/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.TestChannelConfiguration
import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.ContentDetail
import com.enigmastation.streampack.blog.model.ContentListResponse
import com.enigmastation.streampack.blog.model.FindContentRequest
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CommentRepository
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
@Import(TestChannelConfiguration::class)
class FindContentOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var commentRepository: CommentRepository

    private lateinit var author: User
    private lateinit var admin: User
    private lateinit var otherUser: User
    private lateinit var publishedPost: Post
    private lateinit var draftPost: Post
    private lateinit var scheduledPost: Post
    private lateinit var deletedPost: Post

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        author =
            userRepository.save(
                User(
                    username = "author",
                    email = "author@test.com",
                    displayName = "Test Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        admin =
            userRepository.save(
                User(
                    username = "testadmin",
                    email = "testadmin@test.com",
                    displayName = "Admin User",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )
        otherUser =
            userRepository.save(
                User(
                    username = "other",
                    email = "other@test.com",
                    displayName = "Other User",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        publishedPost =
            postRepository.save(
                Post(
                    title = "Published Post",
                    markdownSource = "# Published",
                    renderedHtml = "<h1>Published</h1>",
                    excerpt = "Published content",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                )
            )
        slugRepository.save(
            Slug(path = "2026/02/published-post", post = publishedPost, canonical = true)
        )

        draftPost =
            postRepository.save(
                Post(
                    title = "Draft Post",
                    markdownSource = "# Draft",
                    renderedHtml = "<h1>Draft</h1>",
                    excerpt = "Draft content",
                    status = PostStatus.DRAFT,
                    author = author,
                )
            )
        slugRepository.save(Slug(path = "2026/02/draft-post", post = draftPost, canonical = true))

        scheduledPost =
            postRepository.save(
                Post(
                    title = "Scheduled Post",
                    markdownSource = "# Scheduled",
                    renderedHtml = "<h1>Scheduled</h1>",
                    excerpt = "Scheduled content",
                    status = PostStatus.APPROVED,
                    publishedAt = now.plus(7, ChronoUnit.DAYS),
                    author = author,
                )
            )
        slugRepository.save(
            Slug(path = "2026/02/scheduled-post", post = scheduledPost, canonical = true)
        )

        deletedPost =
            postRepository.save(
                Post(
                    title = "Deleted Post",
                    markdownSource = "# Deleted",
                    renderedHtml = "<h1>Deleted</h1>",
                    excerpt = "Deleted content",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(2, ChronoUnit.HOURS),
                    deleted = true,
                    author = author,
                )
            )
        slugRepository.save(
            Slug(path = "2026/02/deleted-post", post = deletedPost, canonical = true)
        )
    }

    private fun findMessage(request: FindContentRequest, user: User? = null) =
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
    fun `FindBySlug for published post returns ContentDetail`() {
        val result =
            eventGateway.process(
                findMessage(FindContentRequest.FindBySlug("2026/02/published-post"))
            )

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Published Post", detail.title)
        assertEquals("2026/02/published-post", detail.slug)
        assertEquals("<h1>Published</h1>", detail.renderedHtml)
        assertEquals("Test Author", detail.authorDisplayName)
    }

    @Test
    fun `FindBySlug for nonexistent returns error`() {
        val result =
            eventGateway.process(findMessage(FindContentRequest.FindBySlug("2026/02/nonexistent")))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `FindBySlug for draft by non-author returns error`() {
        val result =
            eventGateway.process(
                findMessage(FindContentRequest.FindBySlug("2026/02/draft-post"), otherUser)
            )

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `FindBySlug for draft by author returns ContentDetail`() {
        val result =
            eventGateway.process(
                findMessage(FindContentRequest.FindBySlug("2026/02/draft-post"), author)
            )

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Draft Post", detail.title)
        assertEquals(PostStatus.DRAFT, detail.status)
    }

    @Test
    fun `FindById for published post returns ContentDetail`() {
        val result =
            eventGateway.process(findMessage(FindContentRequest.FindById(publishedPost.id)))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Published Post", detail.title)
        assertNotNull(detail.publishedAt)
    }

    @Test
    fun `FindById for deleted post returns error`() {
        val result = eventGateway.process(findMessage(FindContentRequest.FindById(deletedPost.id)))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `FindPublished returns only published posts`() {
        val result = eventGateway.process(findMessage(FindContentRequest.FindPublished()))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as ContentListResponse
        // Only the one published post should appear (not draft, scheduled, or deleted)
        assertEquals(1, response.posts.size)
        assertEquals("Published Post", response.posts[0].title)
        assertEquals(1, response.totalCount)
    }

    @Test
    fun `FindPublished pagination works`() {
        // Add more published posts
        val now = Instant.now()
        for (i in 1..5) {
            val post =
                postRepository.save(
                    Post(
                        title = "Extra Post $i",
                        markdownSource = "content $i",
                        renderedHtml = "<p>content $i</p>",
                        excerpt = "excerpt $i",
                        status = PostStatus.APPROVED,
                        publishedAt = now.minus(i.toLong(), ChronoUnit.HOURS),
                        author = author,
                    )
                )
            slugRepository.save(Slug(path = "2026/02/extra-post-$i", post = post, canonical = true))
        }

        // Request page 0, size 3 -- should get 3 of the 6 published posts
        val result =
            eventGateway.process(findMessage(FindContentRequest.FindPublished(page = 0, size = 3)))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val response = (result as OperationResult.Success).payload as ContentListResponse
        assertEquals(3, response.posts.size)
        assertEquals(0, response.page)
        assertEquals(2, response.totalPages)
        assertEquals(6, response.totalCount)
    }

    @Test
    fun `admin can see draft via FindById`() {
        val result =
            eventGateway.process(findMessage(FindContentRequest.FindById(draftPost.id), admin))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals("Draft Post", detail.title)
        assertEquals(PostStatus.DRAFT, detail.status)
    }

    @Test
    fun `anonymous user cannot see draft via FindBySlug`() {
        val result =
            eventGateway.process(
                findMessage(FindContentRequest.FindBySlug("2026/02/draft-post"), null)
            )

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Post not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `commentCount reflects active comments at any nesting depth`() {
        // Add top-level and nested comments, plus one soft-deleted
        val topComment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = otherUser,
                    markdownSource = "Top comment",
                    renderedHtml = "<p>Top comment</p>",
                )
            )
        commentRepository.save(
            Comment(
                post = publishedPost,
                author = author,
                markdownSource = "Nested reply",
                renderedHtml = "<p>Nested reply</p>",
                parentComment = topComment,
            )
        )
        commentRepository.save(
            Comment(
                post = publishedPost,
                author = otherUser,
                markdownSource = "Deleted comment",
                renderedHtml = "<p>Deleted comment</p>",
                deleted = true,
            )
        )

        val result =
            eventGateway.process(
                findMessage(FindContentRequest.FindBySlug("2026/02/published-post"))
            )

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        // 2 active comments (top-level + nested), soft-deleted excluded
        assertEquals(2, detail.commentCount)
    }

    @Test
    fun `commentCount is zero when no comments exist`() {
        val result =
            eventGateway.process(findMessage(FindContentRequest.FindById(publishedPost.id)))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val detail = (result as OperationResult.Success).payload as ContentDetail
        assertEquals(0, detail.commentCount)
    }
}
