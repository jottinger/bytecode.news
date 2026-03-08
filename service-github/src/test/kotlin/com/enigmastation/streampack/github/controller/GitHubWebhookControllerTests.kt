/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.controller

import com.enigmastation.streampack.github.entity.GitHubRepo
import com.enigmastation.streampack.github.entity.GitHubSubscription
import com.enigmastation.streampack.github.model.DeliveryMode
import com.enigmastation.streampack.github.repository.GitHubRepoRepository
import com.enigmastation.streampack.github.repository.GitHubSubscriptionRepository
import com.enigmastation.streampack.github.service.WebhookSecretCipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GitHubWebhookControllerTests {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repoRepository: GitHubRepoRepository
    @Autowired lateinit var subscriptionRepository: GitHubSubscriptionRepository
    @Autowired lateinit var cipher: WebhookSecretCipher

    private val secret = "integration-secret"

    @BeforeEach
    fun seedRepo() {
        subscriptionRepository.deleteAll()
        repoRepository.deleteAll()
        val repo =
            repoRepository.save(
                GitHubRepo(
                    owner = "owner",
                    name = "repo",
                    deliveryMode = DeliveryMode.WEBHOOK,
                    webhookSecret = cipher.encrypt(secret),
                )
            )
        subscriptionRepository.save(
            GitHubSubscription(repo = repo, destinationUri = "console:///local")
        )
    }

    @Test
    fun `valid signature returns 202`() {
        val payload =
            """{
            "action":"opened",
            "repository":{"full_name":"owner/repo"},
            "issue":{"number":1,"title":"Test issue","html_url":"https://github.com/owner/repo/issues/1"}
        }"""
        mockMvc
            .post("/webhooks/github") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
                header("X-GitHub-Event", "issues")
                header("X-Hub-Signature-256", sign(payload.toByteArray()))
            }
            .andExpect { status { isAccepted() } }
    }

    @Test
    fun `invalid signature returns 401`() {
        val payload =
            """{
            "action":"opened",
            "repository":{"full_name":"owner/repo"},
            "issue":{"number":1,"title":"Test issue","html_url":"https://github.com/owner/repo/issues/1"}
        }"""
        mockMvc
            .post("/webhooks/github") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
                header("X-GitHub-Event", "issues")
                header("X-Hub-Signature-256", "sha256=badsignature")
            }
            .andExpect { status { isUnauthorized() } }
    }

    private fun sign(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(body)
        val hex = raw.joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }
}
