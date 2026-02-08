/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.config.StreampackProperties
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/** Sends transactional emails for verification and password reset flows */
@Service
class EmailService(private val mailSender: JavaMailSender, properties: StreampackProperties) {
    private val baseUrl = properties.baseUrl
    private val fromAddress = properties.mail.from
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    /** Sends an email verification link to the user */
    fun sendVerificationEmail(to: String, token: String) {
        val link = "$baseUrl/auth/verify?token=$token"
        val message = SimpleMailMessage()
        message.from = fromAddress
        message.setTo(to)
        message.subject = "Verify your email address"
        message.text =
            "Welcome to jvm.news!\n\n" +
                "Please verify your email address by visiting:\n$link\n\n" +
                "This link will expire in 24 hours."
        logger.info("Sending verification email to {}", to)
        mailSender.send(message)
    }

    /** Sends a password reset link to the user */
    fun sendPasswordResetEmail(to: String, token: String) {
        val link = "$baseUrl/auth/reset-password?token=$token"
        val message = SimpleMailMessage()
        message.from = fromAddress
        message.setTo(to)
        message.subject = "Reset your password"
        message.text =
            "A password reset was requested for your jvm.news account.\n\n" +
                "Reset your password by visiting:\n$link\n\n" +
                "This link will expire in 1 hour.\n" +
                "If you did not request this, please ignore this email."
        logger.info("Sending password reset email to {}", to)
        mailSender.send(message)
    }
}
