/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.repository.PostRepository
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import jakarta.servlet.http.HttpServletRequest
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
    @GetMapping("/feed.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun feed(request: HttpServletRequest): ResponseEntity<String> {
        val now = Instant.now()
        val posts = postRepository.findPublished(now)
        val baseUrl = resolveBaseUrl(request)

        val feed = SyndFeedImpl()
        feed.feedType = "rss_2.0"
        feed.title = blogProperties.siteName
        feed.link = baseUrl
        feed.description = "Latest posts on ${blogProperties.siteName}"

        feed.entries =
            posts.mapNotNull { post ->
                val slug = slugRepository.findCanonical(post.id) ?: return@mapNotNull null
                if (!BLOG_POST_SLUG.matches(slug.path)) return@mapNotNull null

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

    private fun resolveBaseUrl(request: HttpServletRequest): String {
        val forwardedProto = request.getHeader("X-Forwarded-Proto")?.substringBefore(',')?.trim()
        val forwardedHost = request.getHeader("X-Forwarded-Host")?.substringBefore(',')?.trim()
        val host = forwardedHost ?: request.getHeader("Host")?.trim()
        val scheme =
            if (forwardedProto == "http" || forwardedProto == "https") forwardedProto
            else request.scheme

        if (host.isNullOrBlank()) {
            return blogProperties.baseUrl.trimEnd('/')
        }

        return "$scheme://$host".trimEnd('/')
    }

    companion object {
        private val BLOG_POST_SLUG = Regex("""\d{4}/\d{2}/.+""")
    }
}
