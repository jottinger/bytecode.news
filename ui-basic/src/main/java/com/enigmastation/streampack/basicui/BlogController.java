/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.basicui;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BlogController {
  private static final int PAGE_SIZE = 10;
  private final BlogApiClient blogApiClient;

  public BlogController(BlogApiClient blogApiClient) {
    this.blogApiClient = blogApiClient;
  }

  @GetMapping("/")
  public String home(
      @RequestParam(name = "page", defaultValue = "0") int page,
      HttpServletRequest request,
      Model model) {
    int safePage = Math.max(page, 0);
    try {
      BlogApiClient.ContentListResponse response = blogApiClient.listPosts(safePage, PAGE_SIZE);
      model.addAttribute("siteHost", request.getServerName());
      model.addAttribute("posts", response.posts() == null ? List.of() : response.posts());
      model.addAttribute("page", safePage);
      model.addAttribute("hasPrev", safePage > 0);
      model.addAttribute("hasNext", safePage + 1 < Math.max(response.totalPages(), 0));
      model.addAttribute("prevPage", Math.max(safePage - 1, 0));
      model.addAttribute("nextPage", safePage + 1);
      return "index";
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load posts", exception);
    }
  }

  @GetMapping("/posts/{year}/{month}/{slug}")
  public String post(
      @PathVariable int year,
      @PathVariable int month,
      @PathVariable String slug,
      HttpServletRequest request,
      Model model) {
    try {
      BlogApiClient.ContentDetail post = blogApiClient.findPostBySlug(year, month, slug);
      BlogApiClient.CommentThreadResponse comments = blogApiClient.findCommentsBySlug(year, month, slug);
      model.addAttribute("siteHost", request.getServerName());
      model.addAttribute("post", post);
      model.addAttribute("comments", comments.comments() == null ? List.of() : comments.comments());
      model.addAttribute("commentCount", comments.totalActiveCount());
      return "post";
    } catch (RestClientException exception) {
      if (blogApiClient.isNotFound(exception)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found", exception);
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load post", exception);
    }
  }

  @GetMapping(value = "/feed.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> feed(HttpServletRequest request) {
    try {
      String host = forwardedHost(request);
      String proto = forwardedProto(request);
      String port = forwardedPort(request, proto);
      BlogApiClient.FeedResponse feed = blogApiClient.fetchFeedXml(host, proto, port);
      MediaType contentType =
          feed.contentType() != null ? feed.contentType() : MediaType.APPLICATION_XML;
      return ResponseEntity.status(feed.status()).contentType(contentType).body(feed.body());
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load feed", exception);
    }
  }

  private String forwardedHost(HttpServletRequest request) {
    String forwardedHost = request.getHeader("X-Forwarded-Host");
    if (forwardedHost != null && !forwardedHost.isBlank()) {
      return forwardedHost.split(",")[0].trim();
    }
    String host = request.getHeader("Host");
    if (host != null && !host.isBlank()) {
      return host.trim();
    }
    return request.getServerName();
  }

  private String forwardedProto(HttpServletRequest request) {
    String forwardedProto = request.getHeader("X-Forwarded-Proto");
    if (forwardedProto != null && !forwardedProto.isBlank()) {
      String value = forwardedProto.split(",")[0].trim().toLowerCase();
      if ("http".equals(value) || "https".equals(value)) {
        return value;
      }
    }
    return request.isSecure() ? "https" : "http";
  }

  private String forwardedPort(HttpServletRequest request, String proto) {
    String forwardedPort = request.getHeader("X-Forwarded-Port");
    if (forwardedPort != null && !forwardedPort.isBlank()) {
      return forwardedPort.split(",")[0].trim();
    }
    int serverPort = request.getServerPort();
    if (serverPort > 0) {
      return String.valueOf(serverPort);
    }
    return "https".equals(proto) ? "443" : "80";
  }
}
