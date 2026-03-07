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
import com.enigmastation.streampack.test.TestChannelConfiguration
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/** Integration tests for the RSS feed HTTP endpoint */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class RssFeedControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var userRepository: UserRepository

    private lateinit var author: User

    @BeforeEach
    fun setUp() {
        author =
            userRepository.save(
                User(
                    username = "rssauthor",
                    email = "rssauthor@test.com",
                    displayName = "RSS Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        val now = Instant.now()
        val post =
            postRepository.save(
                Post(
                    title = "RSS Test Post",
                    markdownSource = "# RSS Test",
                    renderedHtml = "<h1>RSS Test</h1>",
                    excerpt = "RSS test excerpt",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                )
            )
        slugRepository.save(Slug(path = "2026/02/rss-test-post", post = post, canonical = true))
    }

    @Test
    fun `GET rss_xml returns 200 with RSS content type`() {
        mockMvc.get("/blog/rss.xml").andExpect {
            status { isOk() }
            content { contentType("application/rss+xml;charset=utf-8") }
        }
    }

    @Test
    fun `GET rss_xml returns valid XML with channel metadata`() {
        val result =
            mockMvc
                .get("/blog/rss.xml")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString

        assertTrue(result.contains("<rss"))
        assertTrue(result.contains("<channel>"))
        assertTrue(result.contains("<title>bytecode.news</title>"))
        assertTrue(
            result.contains("<description>JVM ecosystem news and community content</description>")
        )
        assertTrue(result.contains("<language>en-us</language>"))
    }

    @Test
    fun `GET rss_xml includes published post as item`() {
        val result =
            mockMvc
                .get("/blog/rss.xml")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString

        assertTrue(result.contains("<item>"))
        assertTrue(result.contains("<title>RSS Test Post</title>"))
        assertTrue(result.contains("2026/02/rss-test-post"))
        assertTrue(result.contains("RSS test excerpt"))
    }

    @Test
    fun `GET rss_xml requires no authentication`() {
        mockMvc.get("/blog/rss.xml").andExpect { status { isOk() } }
    }

    @Test
    fun `GET rss_xml with no posts returns empty feed`() {
        slugRepository.deleteAll()
        postRepository.deleteAll()

        val result =
            mockMvc
                .get("/blog/rss.xml")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString

        assertTrue(result.contains("<channel>"))
        assertTrue(result.contains("<title>bytecode.news</title>"))
    }
}
