/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.basicui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BlogApiClient {
  private final String baseUrl;
  private final RestTemplate restTemplate;

  public BlogApiClient(
      @Value("${BACKEND_SCHEME:http}") String backendScheme,
      @Value("${BACKEND_HOST:backend:8080}") String backendHost) {
    this.baseUrl = backendScheme + "://" + backendHost;
    this.restTemplate = new RestTemplate();
  }

  RestTemplate restTemplate() {
    return restTemplate;
  }

  public ContentListResponse listPosts(int page, int size) {
    String url =
        UriComponentsBuilder.fromUriString(baseUrl)
            .path("/posts")
            .queryParam("page", page)
            .queryParam("size", size)
            .toUriString();
    return restTemplate.getForObject(url, ContentListResponse.class);
  }

  public ContentDetail findPostBySlug(int year, int month, String slug) {
    String url =
        UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("posts", String.valueOf(year), String.valueOf(month), slug)
            .toUriString();
    return restTemplate.getForObject(url, ContentDetail.class);
  }

  public CommentThreadResponse findCommentsBySlug(int year, int month, String slug) {
    String url =
        UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("posts", String.valueOf(year), String.valueOf(month), slug, "comments")
            .toUriString();
    return restTemplate.getForObject(url, CommentThreadResponse.class);
  }

  public FeedResponse fetchFeedXml(String forwardedHost, String forwardedProto, String forwardedPort) {
    String url = UriComponentsBuilder.fromUriString(baseUrl).path("/feed.xml").toUriString();
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_XML));
    if (forwardedHost != null && !forwardedHost.isBlank()) {
      headers.set("Host", forwardedHost);
      headers.set("X-Forwarded-Host", forwardedHost);
    }
    if (forwardedProto != null && !forwardedProto.isBlank()) {
      headers.set("X-Forwarded-Proto", forwardedProto);
    }
    if (forwardedPort != null && !forwardedPort.isBlank()) {
      headers.set("X-Forwarded-Port", forwardedPort);
    }
    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
    ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
    return new FeedResponse(
        response.getStatusCode(), response.getHeaders().getContentType(), response.getBody());
  }

  public boolean isNotFound(RestClientException exception) {
    return exception instanceof HttpStatusCodeException statusCodeException
        && statusCodeException.getStatusCode().value() == 404;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentListResponse(List<ContentSummary> posts, int page, int totalPages, long totalCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentSummary(
      UUID id,
      String title,
      String slug,
      String excerpt,
      String authorDisplayName,
      Instant publishedAt,
      int commentCount,
      List<String> tags,
      List<String> categories) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentDetail(
      UUID id,
      String title,
      String slug,
      String renderedHtml,
      String excerpt,
      UUID authorId,
      String authorDisplayName,
      String status,
      Instant publishedAt,
      Instant createdAt,
      Instant updatedAt,
      int commentCount,
      List<String> tags,
      List<String> categories,
      String markdownSource) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CommentThreadResponse(UUID postId, List<CommentNode> comments, int totalActiveCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CommentNode(
      UUID id,
      UUID authorId,
      String authorDisplayName,
      String renderedHtml,
      Instant createdAt,
      Instant updatedAt,
      boolean deleted,
      boolean editable,
      List<CommentNode> children) {}

  public record FeedResponse(HttpStatusCode status, MediaType contentType, String body) {}
}
