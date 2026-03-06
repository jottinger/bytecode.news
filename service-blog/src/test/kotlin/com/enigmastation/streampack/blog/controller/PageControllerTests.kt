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
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/** Integration tests for the system pages endpoint */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestChannelConfiguration::class)
class PageControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var slugRepository: SlugRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var postCategoryRepository: PostCategoryRepository

    @BeforeEach
    fun setUp() {
        val now = Instant.now()
        val admin =
            userRepository.save(
                User(
                    username = "pageadmin",
                    email = "pageadmin@test.com",
                    displayName = "Page Admin",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )

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
                    author = admin,
                )
            )
        slugRepository.save(Slug(path = "about", post = aboutPost, canonical = true))
        postCategoryRepository.save(PostCategory(post = aboutPost, category = pagesCategory))
    }

    @Test
    fun `GET page by slug returns system page`() {
        mockMvc.get("/pages/about").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("About") }
            jsonPath("$.renderedHtml") { value("<h1>About Us</h1>") }
        }
    }

    @Test
    fun `GET nonexistent page returns 404`() {
        mockMvc.get("/pages/no-such-page").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Page not found") }
        }
    }
}
