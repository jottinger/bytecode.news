/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.service.RssFeedService
import com.rometools.rome.io.SyndFeedOutput
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Serves the blog RSS 2.0 feed */
@RestController
class RssFeedController(private val rssFeedService: RssFeedService) {

    @GetMapping("/blog/rss.xml")
    fun rssFeed(): ResponseEntity<String> {
        val feed = rssFeedService.buildFeed()
        val xml = SyndFeedOutput().outputString(feed)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/rss+xml; charset=utf-8"))
            .body(xml)
    }
}
