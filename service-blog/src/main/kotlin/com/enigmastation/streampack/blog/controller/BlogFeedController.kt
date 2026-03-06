/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import java.time.Instant
import java.util.Date
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Generates an RSS 2.0 feed of published blog posts */
@RestController
class BlogFeedController(
    private val postRepository: PostRepository,
    private val slugRepository: SlugRepository,
    private val blogProperties: BlogProperties,
) {
    private val baseUrl
        get() = blogProperties.baseUrl.trimEnd('/')

    @GetMapping("/feed.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun feed(): ResponseEntity<String> {
        val now = Instant.now()
        val posts = postRepository.findPublished(now)

        val feed = SyndFeedImpl()
        feed.feedType = "rss_2.0"
        feed.title = blogProperties.siteName
        feed.link = baseUrl
        feed.description = "Latest posts on ${blogProperties.siteName}"

        feed.entries =
            posts.mapNotNull { post ->
                val slug = slugRepository.findCanonical(post.id) ?: return@mapNotNull null
                val entry = SyndEntryImpl()
                entry.title = post.title
                entry.link = "$baseUrl/posts/${slug.path}"
                entry.publishedDate = Date.from(post.publishedAt ?: post.createdAt)
                entry.author = post.author?.displayName ?: "Anonymous"

                val content = SyndContentImpl()
                content.type = "text/html"
                content.value = post.renderedHtml
                entry.description = content

                entry
            }

        val output = SyndFeedOutput()
        val xml = output.outputString(feed)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml)
    }
}
