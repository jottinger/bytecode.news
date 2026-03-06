/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.PostCategory
import com.enigmastation.streampack.blog.entity.Slug
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.test.TestChannelConfiguration
import java.time.Instant
import java.time.ZoneOffset
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
import org.springframework.transaction.annotation.Transactional

/** Integration tests for server-side rendered HTML pages */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class SsrControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var postCategoryRepository: PostCategoryRepository

    private lateinit var author: User
    private lateinit var publishedPost: Post
    private lateinit var slugPath: String

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        author =
            userRepository.save(
                User(
                    username = "ssrauthor",
                    email = "ssrauthor@test.com",
                    displayName = "SSR Author",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )

        publishedPost =
            postRepository.save(
                Post(
                    title = "SSR Test Post",
                    markdownSource = "# Hello SSR",
                    renderedHtml = "<h1>Hello SSR</h1>",
                    excerpt = "A post for SSR testing",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                )
            )

        val publishedAt = publishedPost.publishedAt!!
        val dateTime = publishedAt.atZone(ZoneOffset.UTC)
        slugPath = "${dateTime.year}/${"%02d".format(dateTime.monthValue)}/ssr-test-post"
        slugRepository.save(Slug(path = slugPath, post = publishedPost, canonical = true))

        // Set up a system page
        val pagesCategory = categoryRepository.findByName("_pages")!!
        val aboutPost =
            postRepository.save(
                Post(
                    title = "About",
                    markdownSource = "# About Us",
                    renderedHtml = "<h1>About Us</h1>",
                    excerpt = "About this site",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                )
            )
        slugRepository.save(Slug(path = "about", post = aboutPost, canonical = true))
        postCategoryRepository.save(PostCategory(post = aboutPost, category = pagesCategory))
    }

    @Test
    fun `SSR home returns HTML with post listing`() {
        mockMvc
            .get("/ssr/") { accept = MediaType.TEXT_HTML }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("SSR Test Post")) }
                content { string(org.hamcrest.Matchers.containsString("<!DOCTYPE html>")) }
                content { string(org.hamcrest.Matchers.containsString("og:title")) }
            }
    }

    @Test
    fun `SSR post returns HTML with article content`() {
        val parts = slugPath.split("/")
        val year = parts[0]
        val month = parts[1]
        val slug = parts[2]

        mockMvc
            .get("/ssr/posts/$year/$month/$slug") { accept = MediaType.TEXT_HTML }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("SSR Test Post")) }
                content { string(org.hamcrest.Matchers.containsString("<h1>Hello SSR</h1>")) }
                content { string(org.hamcrest.Matchers.containsString("A post for SSR testing")) }
                content { string(org.hamcrest.Matchers.containsString("canonical")) }
            }
    }

    @Test
    fun `SSR post returns 404 HTML for unknown slug`() {
        mockMvc
            .get("/ssr/posts/2025/01/nonexistent") { accept = MediaType.TEXT_HTML }
            .andExpect {
                status { isNotFound() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("Not Found")) }
            }
    }

    @Test
    fun `SSR page returns HTML for system page`() {
        mockMvc
            .get("/ssr/pages/about") { accept = MediaType.TEXT_HTML }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("About")) }
                content { string(org.hamcrest.Matchers.containsString("<h1>About Us</h1>")) }
            }
    }

    @Test
    fun `SSR page returns 404 HTML for unknown page`() {
        mockMvc
            .get("/ssr/pages/no-such-page") { accept = MediaType.TEXT_HTML }
            .andExpect {
                status { isNotFound() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("Not Found")) }
            }
    }

    @Test
    fun `SSR home includes meta tags for SEO`() {
        mockMvc
            .get("/ssr/") { accept = MediaType.TEXT_HTML }
            .andExpect {
                content { string(org.hamcrest.Matchers.containsString("og:description")) }
                content { string(org.hamcrest.Matchers.containsString("robots")) }
                content { string(org.hamcrest.Matchers.containsString("index, follow")) }
            }
    }
}
