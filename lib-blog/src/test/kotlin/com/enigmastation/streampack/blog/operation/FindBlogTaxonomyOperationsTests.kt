/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Category
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.entity.PostCategory
import com.enigmastation.streampack.blog.entity.PostTag
import com.enigmastation.streampack.blog.entity.Tag
import com.enigmastation.streampack.blog.model.PostStatus
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.blog.repository.PostCategoryRepository
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.PostTagRepository
import com.enigmastation.streampack.blog.repository.TagRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.taxonomy.model.FindBlogCategoryTaxonomyRequest
import com.enigmastation.streampack.taxonomy.model.FindBlogTagTaxonomyRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomyTermCount
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FindBlogTaxonomyOperationsTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var tagRepository: TagRepository
    @Autowired lateinit var postTagRepository: PostTagRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var postCategoryRepository: PostCategoryRepository

    private fun message(payload: Any) =
        MessageBuilder.withPayload(payload)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.CONSOLE, serviceId = "test", replyTo = "local"),
            )
            .build()

    @BeforeEach
    fun setUp() {
        val author =
            userRepository.save(
                User(
                    username = "taxonomy-blog-author",
                    email = "taxonomy-blog@author.test",
                    displayName = "Taxonomy Blog Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        val postOne =
            postRepository.save(
                Post(
                    title = "Taxonomy Post 1",
                    markdownSource = "Body 1",
                    renderedHtml = "<p>Body 1</p>",
                    excerpt = "One",
                    status = PostStatus.APPROVED,
                    publishedAt = Instant.now(),
                    author = author,
                )
            )
        val postTwo =
            postRepository.save(
                Post(
                    title = "Taxonomy Post 2",
                    markdownSource = "Body 2",
                    renderedHtml = "<p>Body 2</p>",
                    excerpt = "Two",
                    status = PostStatus.APPROVED,
                    publishedAt = Instant.now(),
                    author = author,
                )
            )

        val xyzTag = tagRepository.save(Tag(name = "xyz", slug = "xyz"))
        val kotlinTag = tagRepository.save(Tag(name = "kotlin", slug = "kotlin"))
        val hiddenTag = tagRepository.save(Tag(name = "_sidebar", slug = "sidebar-hidden"))
        postTagRepository.save(PostTag(post = postOne, tag = xyzTag))
        postTagRepository.save(PostTag(post = postOne, tag = kotlinTag))
        postTagRepository.save(PostTag(post = postTwo, tag = xyzTag))
        postTagRepository.save(PostTag(post = postTwo, tag = hiddenTag))

        val guidesCategory = categoryRepository.save(Category(name = "guides", slug = "guides"))
        val xyzCategory = categoryRepository.save(Category(name = "xyz", slug = "xyz-category"))
        val hiddenCategory =
            categoryRepository.save(Category(name = "_ideas", slug = "ideas-hidden"))
        postCategoryRepository.save(PostCategory(post = postOne, category = guidesCategory))
        postCategoryRepository.save(PostCategory(post = postTwo, category = xyzCategory))
        postCategoryRepository.save(PostCategory(post = postTwo, category = hiddenCategory))
    }

    @Test
    fun `blog tag taxonomy returns grouped counts and excludes underscore terms`() {
        val result = eventGateway.process(message(FindBlogTagTaxonomyRequest))
        assertInstanceOf(OperationResult.Success::class.java, result)

        val tags = (result as OperationResult.Success).payload as List<*>
        val counts = tags.filterIsInstance<TaxonomyTermCount>().associate { it.name to it.count }

        assertEquals(2L, counts["xyz"])
        assertEquals(1L, counts["kotlin"])
        assertTrue("_sidebar" !in counts.keys)
    }

    @Test
    fun `blog category taxonomy returns grouped counts and excludes underscore terms`() {
        val result = eventGateway.process(message(FindBlogCategoryTaxonomyRequest))
        assertInstanceOf(OperationResult.Success::class.java, result)

        val categories = (result as OperationResult.Success).payload as List<*>
        val counts =
            categories.filterIsInstance<TaxonomyTermCount>().associate { it.name to it.count }

        assertEquals(1L, counts["guides"])
        assertEquals(1L, counts["xyz"])
        assertTrue("_ideas" !in counts.keys)
    }
}
