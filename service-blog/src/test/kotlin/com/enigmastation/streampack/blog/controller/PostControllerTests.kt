/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.PostStatus
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** Integration tests for public and authenticated post endpoints */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
@TestPropertySource(properties = ["streampack.blog.anonymous-submission=true"])
class PostControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var jwtService: JwtService

    private lateinit var verifiedUser: User
    private lateinit var verifiedUserToken: String
    private lateinit var unverifiedUser: User
    private lateinit var unverifiedUserToken: String
    private lateinit var publishedPost: Post
    private lateinit var slugPath: String

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        verifiedUser =
            userRepository.save(
                User(
                    username = "poster",
                    email = "poster@test.com",
                    displayName = "Test Poster",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        verifiedUserToken = jwtService.generateToken(verifiedUser.toUserPrincipal())

        unverifiedUser =
            userRepository.save(
                User(
                    username = "unverifiedposter",
                    email = "unverifiedposter@test.com",
                    displayName = "Unverified Poster",
                    emailVerified = false,
                    role = Role.USER,
                )
            )
        unverifiedUserToken = jwtService.generateToken(unverifiedUser.toUserPrincipal())

        publishedPost =
            postRepository.save(
                Post(
                    title = "Published Post",
                    markdownSource = "Published content.",
                    renderedHtml = "<p>Published content.</p>",
                    excerpt = "Published content.",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = verifiedUser,
                    createdAt = now,
                    updatedAt = now,
                )
            )

        slugPath = "2026/01/published-post"
        slugRepository.save(
            Slug(path = slugPath, post = publishedPost, canonical = true, createdAt = now)
        )
    }

    // --- GET /posts ---

    @Test
    fun `GET posts returns paginated list`() {
        mockMvc.get("/posts").andExpect {
            status { isOk() }
            jsonPath("$.posts") { isArray() }
            jsonPath("$.page") { value(0) }
            jsonPath("$.totalCount") { isNumber() }
        }
    }

    // --- GET /posts/{year}/{month}/{slug} ---

    @Test
    fun `GET post by slug returns detail`() {
        mockMvc.get("/posts/$slugPath").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(publishedPost.id.toString()) }
            jsonPath("$.title") { value("Published Post") }
            jsonPath("$.renderedHtml") { isNotEmpty() }
        }
    }

    @Test
    fun `GET nonexistent slug returns 404`() {
        mockMvc.get("/posts/2026/01/no-such-post").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Post not found") }
        }
    }

    // --- GET /posts/search ---

    @Test
    fun `GET search returns results`() {
        mockMvc.get("/posts/search?q=Published").andExpect {
            status { isOk() }
            jsonPath("$.posts") { isArray() }
        }
    }

    // --- POST /posts ---

    @Test
    fun `POST creates draft and returns 201`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content =
                    """{"title":"My New Post","markdownSource":"Hello world.","formLoadedAt":${System.currentTimeMillis() - 5000}}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.title") { value("My New Post") }
                jsonPath("$.status") { value("DRAFT") }
                jsonPath("$.authorDisplayName") { value("Test Poster") }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.slug") { isNotEmpty() }
            }
    }

    @Test
    fun `POST anonymous submission creates draft`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"Community Post","markdownSource":"Anonymous content.","formLoadedAt":${System.currentTimeMillis() - 5000}}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.title") { value("Community Post") }
                jsonPath("$.status") { value("DRAFT") }
                jsonPath("$.authorDisplayName") { value("Anonymous") }
                jsonPath("$.id") { isNotEmpty() }
            }
    }

    @Test
    fun `POST with unverified email returns 400`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $unverifiedUserToken")
                content =
                    """{"title":"My Post","markdownSource":"Content.","formLoadedAt":${System.currentTimeMillis() - 5000}}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Email verification required") }
            }
    }

    @Test
    fun `POST with blank title returns 400`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content =
                    """{"title":"","markdownSource":"Content.","formLoadedAt":${System.currentTimeMillis() - 5000}}"""
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Title is required") }
            }
    }

    // --- PUT /posts/{id} ---

    @Test
    fun `PUT edits post owned by author`() {
        val draftPost =
            postRepository.save(
                Post(
                    title = "Draft Post",
                    markdownSource = "Original.",
                    renderedHtml = "<p>Original.</p>",
                    excerpt = "Original.",
                    status = PostStatus.DRAFT,
                    author = verifiedUser,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )

        mockMvc
            .put("/posts/${draftPost.id}") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $verifiedUserToken")
                content = """{"title":"Updated Title","markdownSource":"Updated content."}"""
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Updated Title") }
            }
    }

    @Test
    fun `PUT unauthenticated returns 401`() {
        mockMvc
            .put("/posts/${publishedPost.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Updated Title","markdownSource":"Updated content."}"""
            }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Authentication required") }
            }
    }

    // --- Honeypot and timing checks ---

    @Test
    fun `POST with honeypot field populated returns fake 201`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"Spam Post","markdownSource":"Buy stuff.","website":"http://spam.example.com"}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.title") { value("Submitted") }
            }
    }

    @Test
    fun `POST submitted too quickly returns fake 201`() {
        val justNow = System.currentTimeMillis()
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"title":"Fast Post","markdownSource":"Content.","formLoadedAt":$justNow}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.title") { value("Submitted") }
            }
    }

    @Test
    fun `POST with null formLoadedAt returns fake 201`() {
        mockMvc
            .post("/posts") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"No Timing","markdownSource":"Content."}"""
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.title") { value("Submitted") }
            }
    }
}
