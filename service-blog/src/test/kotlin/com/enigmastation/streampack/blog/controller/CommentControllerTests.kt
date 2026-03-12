/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.entity.Category
import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.PostCategory
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.blog.repository.CommentRepository
import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.JwtService
import com.enigmastation.streampack.test.TestChannelConfiguration
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for comment read, create, and edit endpoints */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class CommentControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var postCategoryRepository: PostCategoryRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var commentRepository: CommentRepository
    @Autowired lateinit var jwtService: JwtService

    private lateinit var verifiedUser: User
    private lateinit var verifiedUserToken: String
    private lateinit var unverifiedUser: User
    private lateinit var unverifiedUserToken: String
    private lateinit var otherUser: User
    private lateinit var otherUserToken: String
    private lateinit var publishedPost: Post
    private lateinit var slugPath: String

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
        verifiedUserToken = jwtService.generateToken(verifiedUser.toUserPrincipal())

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
        unverifiedUserToken = jwtService.generateToken(unverifiedUser.toUserPrincipal())

        otherUser =
            userRepository.save(
                User(
                    username = "otheruser",
                    email = "other@test.com",
                    displayName = "Other User",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        otherUserToken = jwtService.generateToken(otherUser.toUserPrincipal())

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

        slugPath = "2026/01/test-post"
        slugRepository.save(
            Slug(path = slugPath, post = publishedPost, canonical = true, createdAt = now)
        )
    }

    // --- GET comments ---

    @Test
    fun `GET returns comment tree as JSON`() {
        commentRepository.save(
            Comment(
                post = publishedPost,
                author = verifiedUser,
                markdownSource = "A comment.",
                renderedHtml = "<p>A comment.</p>",
            )
        )

        mockMvc.get("/posts/$slugPath/comments").andExpect {
            status { isOk() }
            jsonPath("$.postId") { value(publishedPost.id.toString()) }
            jsonPath("$.comments.length()") { value(1) }
            jsonPath("$.totalActiveCount") { value(1) }
        }
    }

    @Test
    fun `GET with slug-based path resolves correctly`() {
        mockMvc.get("/posts/$slugPath/comments").andExpect {
            status { isOk() }
            jsonPath("$.postId") { value(publishedPost.id.toString()) }
            jsonPath("$.comments.length()") { value(0) }
        }
    }

    @Test
    fun `GET on nonexistent slug returns 404`() {
        mockMvc.get("/posts/2026/01/no-such-post/comments").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Post not found") }
        }
    }

    // --- POST create comment ---

    @Test
    fun `POST creates comment and returns 201`() {
        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"This is my comment."}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.postId") { value(publishedPost.id.toString()) }
                jsonPath("$.authorDisplayName") { value("Test Commenter") }
                jsonPath("$.renderedHtml") { isNotEmpty() }
                jsonPath("$.id") { isNotEmpty() }
            }
    }

    @Test
    fun `POST with parent comment ID creates nested reply`() {
        val parentComment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "Parent comment.",
                    renderedHtml = "<p>Parent comment.</p>",
                )
            )

        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content =
                    """{"parentCommentId":"${parentComment.id}","markdownSource":"A reply."}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.postId") { value(publishedPost.id.toString()) }
            }
    }

    @Test
    fun `POST unauthenticated returns 401`() {
        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"markdownSource":"A comment."}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Authentication required") }
            }
    }

    @Test
    fun `POST with unverified email returns 400`() {
        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $unverifiedUserToken")
                content = """{"markdownSource":"A comment."}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Email verification required") }
            }
    }

    @Test
    fun `POST blank markdown returns 400`() {
        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"   "}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Comment content is required") }
            }
    }

    @Test
    fun `POST to sidebar content returns 400`() {
        val sidebarCategory =
            categoryRepository.findByName("_sidebar")
                ?: categoryRepository.save(Category(name = "_sidebar", slug = "_sidebar"))
        postCategoryRepository.save(PostCategory(post = publishedPost, category = sidebarCategory))

        mockMvc
            .post("/posts/$slugPath/comments") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"A comment."}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Comments are disabled for sidebar content") }
            }
    }

    // --- PUT edit comment ---

    @Test
    fun `PUT edits comment within 5 min window`() {
        val comment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "Original.",
                    renderedHtml = "<p>Original.</p>",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )

        mockMvc
            .put("/comments/${comment.id}") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"Updated."}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(comment.id.toString()) }
                jsonPath("$.renderedHtml") { isNotEmpty() }
            }
    }

    @Test
    fun `PUT after 5 min returns 403`() {
        val comment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "Old comment.",
                    renderedHtml = "<p>Old comment.</p>",
                    createdAt = Instant.now().minus(10, ChronoUnit.MINUTES),
                    updatedAt = Instant.now().minus(10, ChronoUnit.MINUTES),
                )
            )

        mockMvc
            .put("/comments/${comment.id}") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"Updated."}"""
            }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.detail") { value("Edit window has expired") }
            }
    }

    @Test
    fun `PUT by non-author returns 403`() {
        val comment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "A comment.",
                    renderedHtml = "<p>A comment.</p>",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )

        mockMvc
            .put("/comments/${comment.id}") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $otherUserToken")
                content = """{"markdownSource":"Hijacked."}"""
            }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.detail") { value("Not authorized to edit this comment") }
            }
    }

    @Test
    fun `PUT unauthenticated returns 401`() {
        val comment =
            commentRepository.save(
                Comment(
                    post = publishedPost,
                    author = verifiedUser,
                    markdownSource = "A comment.",
                    renderedHtml = "<p>A comment.</p>",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )

        mockMvc
            .put("/comments/${comment.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"markdownSource":"Updated."}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Authentication required") }
            }
    }

    @Test
    fun `PUT nonexistent comment returns 404`() {
        mockMvc
            .put("/comments/00000000-0000-0000-0000-000000000001") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"markdownSource":"Updated."}"""
            }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.detail") { value("Comment not found") }
            }
    }
}
