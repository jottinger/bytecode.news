/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.basicui;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "BACKEND_SCHEME=http",
      "BACKEND_HOST=backend.test"
    })
class BlogControllerTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private BlogApiClient blogApiClient;

  private MockRestServiceServer server;

  @BeforeEach
  void setupServer() {
    server = MockRestServiceServer.bindTo(blogApiClient.restTemplate()).build();
  }

  @Test
  void homeRendersPostList() throws Exception {
    server
        .expect(requestTo("http://backend.test/posts?page=0&size=10"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "posts":[{"id":"00000000-0000-0000-0000-000000000001","title":"Entry One","slug":"2026/03/entry-one","excerpt":"Short summary","authorDisplayName":"dreamreal","publishedAt":"2026-03-10T12:00:00Z","commentCount":2,"tags":[],"categories":[]}],
                  "page":0,
                  "totalPages":1,
                  "totalCount":1
                }
                """,
                APPLICATION_JSON));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Entry One")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/posts/2026/03/entry-one")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("2 comments")));

    server.verify();
  }

  @Test
  void postRendersBodyAndComments() throws Exception {
    server
        .expect(requestTo("http://backend.test/posts/2026/3/entry-two"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "id":"00000000-0000-0000-0000-000000000002",
                  "title":"Entry Two",
                  "slug":"2026/03/entry-two",
                  "renderedHtml":"<p>Hello world</p>",
                  "authorDisplayName":"dreamreal",
                  "status":"APPROVED",
                  "createdAt":"2026-03-10T12:00:00Z",
                  "updatedAt":"2026-03-10T12:00:00Z",
                  "commentCount":1,
                  "tags":[],
                  "categories":[]
                }
                """,
                APPLICATION_JSON));
    server
        .expect(requestTo("http://backend.test/posts/2026/3/entry-two/comments"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "postId":"00000000-0000-0000-0000-000000000002",
                  "comments":[{"id":"00000000-0000-0000-0000-000000000003","authorDisplayName":"blue","renderedHtml":"<p>nice</p>","createdAt":"2026-03-10T12:01:00Z","updatedAt":"2026-03-10T12:01:00Z","deleted":false,"editable":false,"children":[]}],
                  "totalActiveCount":1
                }
                """,
                APPLICATION_JSON));

    mockMvc
        .perform(get("/posts/2026/03/entry-two"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Entry Two")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello world")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("nice")));

    server.verify();
  }

  @Test
  void feedIsProxied() throws Exception {
    server
        .expect(requestTo("http://backend.test/feed.xml"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Host", "bytecode.news"))
        .andExpect(header("X-Forwarded-Host", "bytecode.news"))
        .andExpect(header("X-Forwarded-Proto", "https"))
        .andExpect(header("X-Forwarded-Port", "443"))
        .andRespond(
            withSuccess(
                "<rss><channel><title>ByteCode.News</title></channel></rss>", APPLICATION_XML));

    mockMvc
        .perform(
            get("/feed.xml")
                .header("Host", "bytecode.news")
                .header("X-Forwarded-Host", "bytecode.news")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")
                .secure(true))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xml"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("ByteCode.News")));

    server.verify();
  }
}
