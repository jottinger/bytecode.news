/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.service

import com.enigmastation.streampack.blog.config.BlogProperties
import com.enigmastation.streampack.blog.entity.Comment
import com.enigmastation.streampack.blog.entity.Post
import com.enigmastation.streampack.blog.repository.SlugRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.repository.UserRepository
import com.enigmastation.streampack.core.service.MailNotificationPublisher
import org.springframework.stereotype.Service

/** Resolves recipients and publishes email notifications for blog submissions and replies. */
// @lat: [[operations#User-Facing Operations#Blog Notifications]]
@Service
class BlogNotificationService(
    private val userRepository: UserRepository,
    private val slugRepository: SlugRepository,
    private val mailNotificationPublisher: MailNotificationPublisher,
    private val blogProperties: BlogProperties,
) {
    fun notifyPostSubmission(post: Post, slugPath: String, submitter: User?) {
        if (submitter?.role == Role.ADMIN || submitter?.role == Role.SUPER_ADMIN) {
            return
        }

        val body = buildString {
            appendLine("A new post draft was submitted on bytecode.news.")
            appendLine()
            appendLine("Title: ${post.title}")
            appendLine("Author: ${submitter?.displayName ?: "Anonymous"}")
            appendLine("URL: ${buildPostUrl(slugPath)}")
            appendLine()
            appendLine(post.markdownSource)
        }

        mailNotificationPublisher.publishAll(
            userRepository.findDistinctActiveAdminEmailAddresses(),
            "New post submission: ${post.title}",
            body.trim(),
        )
    }

    fun notifyComment(post: Post, parentComment: Comment?, comment: Comment) {
        val recipient = resolveRecipient(post, parentComment) ?: return
        if (
            recipient.id == comment.author.id || !recipient.isActive() || recipient.email.isBlank()
        ) {
            return
        }

        val slugPath = slugRepository.findCanonical(post.id)?.path ?: return
        val isReply = parentComment != null
        val subject =
            if (isReply) {
                "New reply on: ${post.title}"
            } else {
                "New comment on: ${post.title}"
            }
        val body = buildString {
            appendLine(
                if (isReply) {
                    "Your comment on \"${post.title}\" received a new reply."
                } else {
                    "Your post \"${post.title}\" received a new comment."
                }
            )
            appendLine()
            appendLine("URL: ${buildPostUrl(slugPath)}")
            appendLine()
            appendLine(comment.markdownSource)
        }

        mailNotificationPublisher.publish(recipient.email, subject, body.trim())
    }

    private fun resolveRecipient(post: Post, parentComment: Comment?): User? =
        if (parentComment != null) {
            parentComment.author.takeIf { !parentComment.deleted }
        } else {
            post.author
        }

    private fun buildPostUrl(slugPath: String): String =
        "${blogProperties.baseUrl.trimEnd('/')}/posts/$slugPath"
}
