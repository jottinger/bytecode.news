/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.mail.service

import com.enigmastation.streampack.core.config.StreampackProperties
import com.enigmastation.streampack.core.integration.EgressSubscriber
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/** Watches the egress channel for mailto-provenance results and delivers them as email */
@Component
@ConditionalOnProperty("streampack.mail.enabled", havingValue = "true")
class MailEgressSubscriber(
    private val mailSender: JavaMailSender,
    private val properties: StreampackProperties,
) : EgressSubscriber() {

    private val logger = LoggerFactory.getLogger(MailEgressSubscriber::class.java)

    override fun matches(provenance: Provenance): Boolean = provenance.protocol == Protocol.MAILTO

    override fun deliver(result: OperationResult, provenance: Provenance) {
        val text =
            when (result) {
                is OperationResult.Success -> result.payload.toString()
                is OperationResult.Error -> "Error: ${result.message}"
                is OperationResult.NotHandled -> return
            }

        val to = provenance.replyTo
        val message = SimpleMailMessage()
        message.from = properties.mail.from
        message.setTo(to)
        message.subject = "Nevet notification"
        message.text = text

        logger.info("Sending notification email to {}", to)
        mailSender.send(message)
    }
}
