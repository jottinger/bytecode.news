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

/** Integration tests for blog RSS feed generation */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class BlogFeedControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository

    private lateinit var slugPath: String

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        val author =
            userRepository.save(
                User(
                    username = "feedauthor",
                    email = "feedauthor@test.com",
                    displayName = "Feed Author",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )

        val post =
            postRepository.save(
                Post(
                    title = "Feed Test Post",
                    markdownSource = "Feed content",
                    renderedHtml = "<p>Feed content</p>",
                    excerpt = "A post for feed testing",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(1, ChronoUnit.HOURS),
                    author = author,
                )
            )

        val publishedAt = post.publishedAt!!
        val dateTime = publishedAt.atZone(ZoneOffset.UTC)
        slugPath = "${dateTime.year}/${"%02d".format(dateTime.monthValue)}/feed-test-post"
        slugRepository.save(Slug(path = slugPath, post = post, canonical = true))

        // Draft post should NOT appear in feed
        postRepository.save(
            Post(
                title = "Draft Feed Post",
                markdownSource = "Draft",
                renderedHtml = "<p>Draft</p>",
                status = PostStatus.DRAFT,
                author = author,
            )
        )

        // Approved system-style page should NOT appear in blog RSS entries
        val pagePost =
            postRepository.save(
                Post(
                    title = "About",
                    markdownSource = "About page",
                    renderedHtml = "<p>About page</p>",
                    excerpt = "About page excerpt",
                    status = PostStatus.APPROVED,
                    publishedAt = now.minus(2, ChronoUnit.HOURS),
                    author = author,
                )
            )
        slugRepository.save(Slug(path = "about", post = pagePost, canonical = true))
    }

    @Test
    fun `feed returns valid RSS 2_0 XML with published posts`() {
        mockMvc.get("/feed.xml").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_XML) }
            content { string(org.hamcrest.Matchers.containsString("<rss")) }
            content { string(org.hamcrest.Matchers.containsString("Feed Test Post")) }
            content { string(org.hamcrest.Matchers.containsString(slugPath)) }
            content { string(org.hamcrest.Matchers.containsString("Feed Author")) }
        }
    }

    @Test
    fun `feed excludes draft posts`() {
        mockMvc.get("/feed.xml").andExpect {
            status { isOk() }
            content {
                string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Draft Feed Post")
                    )
                )
            }
        }
    }

    @Test
    fun `feed excludes non blog style slugs`() {
        mockMvc.get("/feed.xml").andExpect {
            status { isOk() }
            content {
                string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">About</title>")
                    )
                )
            }
            content {
                string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/posts/about"))
                )
            }
        }
    }

    @Test
    fun `feed includes channel metadata`() {
        mockMvc.get("/feed.xml").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Nevet</title>")) }
            content { string(org.hamcrest.Matchers.containsString("<description>")) }
        }
    }

    @Test
    fun `feed uses forwarded host and proto for public links`() {
        mockMvc
            .get("/feed.xml") {
                header("X-Forwarded-Proto", "https")
                header("X-Forwarded-Host", "foo.bytecode.news")
            }
            .andExpect {
                status { isOk() }
                content {
                    string(
                        org.hamcrest.Matchers.containsString(
                            "<link>https://foo.bytecode.news</link>"
                        )
                    )
                }
                content {
                    string(
                        org.hamcrest.Matchers.containsString(
                            "<guid>https://foo.bytecode.news/posts/$slugPath</guid>"
                        )
                    )
                }
                content {
                    string(
                        org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("localhost:3001")
                        )
                    )
                }
            }
    }
}
