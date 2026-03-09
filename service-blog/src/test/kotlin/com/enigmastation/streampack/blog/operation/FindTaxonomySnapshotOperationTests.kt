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
import com.enigmastation.streampack.taxonomy.model.FindTaxonomySnapshotRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomySnapshot
import com.enigmastation.streampack.test.TestChannelConfiguration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
class FindTaxonomySnapshotOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var tagRepository: TagRepository
    @Autowired lateinit var postTagRepository: PostTagRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var postCategoryRepository: PostCategoryRepository

    private fun commandMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.CONSOLE, serviceId = "test", replyTo = "local"),
            )
            .setHeader("nick", "tester")
            .build()

    private fun requestMessage() =
        MessageBuilder.withPayload(FindTaxonomySnapshotRequest as Any)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.HTTP, serviceId = "test", replyTo = "taxonomy"),
            )
            .build()

    private fun aiRequestMessage() =
        MessageBuilder.withPayload(FindTaxonomySnapshotRequest as Any)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.IRC, serviceId = "ai-kit", replyTo = "#ai"),
            )
            .setHeader("nick", "assistant")
            .build()

    @BeforeEach
    fun setUp() {
        val author =
            userRepository.save(
                User(
                    username = "taxonomy-operation-author",
                    email = "taxonomy-operation@author.test",
                    displayName = "Taxonomy Operation Author",
                    emailVerified = true,
                    role = Role.USER,
                )
            )

        val postOne =
            postRepository.save(
                Post(
                    title = "Taxonomy Operation Post One",
                    markdownSource = "Body one",
                    renderedHtml = "<p>Body one</p>",
                    excerpt = "One",
                    status = PostStatus.APPROVED,
                    publishedAt = Instant.now(),
                    author = author,
                )
            )
        val postTwo =
            postRepository.save(
                Post(
                    title = "Taxonomy Operation Post Two",
                    markdownSource = "Body two",
                    renderedHtml = "<p>Body two</p>",
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

        eventGateway.process(commandMessage("alpha=one"))
        eventGateway.process(commandMessage("alpha.tags=xyz,tools,_page"))
        eventGateway.process(commandMessage("beta=two"))
        eventGateway.process(commandMessage("beta.tags=xyz"))
    }

    @Test
    fun `operation returns merged taxonomy snapshot`() {
        val result = eventGateway.process(requestMessage())

        assertInstanceOf(OperationResult.Success::class.java, result)
        val snapshot = (result as OperationResult.Success).payload as TaxonomySnapshot

        assertEquals(4L, snapshot.tags["xyz"])
        assertEquals(1L, snapshot.tags["kotlin"])
        assertEquals(1L, snapshot.tags["tools"])

        assertEquals(1L, snapshot.categories["guides"])
        assertEquals(1L, snapshot.categories["xyz"])

        assertEquals(5L, snapshot.aggregate["xyz"])
        assertEquals(1L, snapshot.aggregate["guides"])
        assertEquals(1L, snapshot.aggregate["kotlin"])
        assertEquals(1L, snapshot.aggregate["tools"])

        assertFalse(snapshot.tags.keys.any { it.startsWith("_") })
        assertFalse(snapshot.categories.keys.any { it.startsWith("_") })
        assertFalse(snapshot.aggregate.keys.any { it.startsWith("_") })
    }

    @Test
    fun `operation supports non-controller callers`() {
        val result = eventGateway.process(aiRequestMessage())

        assertInstanceOf(OperationResult.Success::class.java, result)
        val snapshot = (result as OperationResult.Success).payload as TaxonomySnapshot

        assertEquals(4L, snapshot.tags["xyz"])
        assertEquals(1L, snapshot.categories["xyz"])
        assertEquals(5L, snapshot.aggregate["xyz"])
        assertFalse(snapshot.aggregate.keys.any { it.startsWith("_") })
    }
}
