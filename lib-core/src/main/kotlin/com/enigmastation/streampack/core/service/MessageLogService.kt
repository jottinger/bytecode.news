/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.entity.MessageLog
import com.enigmastation.streampack.core.model.MessageDirection
import com.enigmastation.streampack.core.repository.MessageLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Captures all messages to the protocol-agnostic message log. Failures never disrupt processing.
 */
@Service
class MessageLogService(private val repository: MessageLogRepository) {
    private val logger = LoggerFactory.getLogger(MessageLogService::class.java)

    fun logInbound(provenanceUri: String, sender: String, content: String) {
        log(provenanceUri, MessageDirection.INBOUND, sender, content)
    }

    fun logOutbound(provenanceUri: String, sender: String, content: String) {
        log(provenanceUri, MessageDirection.OUTBOUND, sender, content)
    }

    private fun log(
        provenanceUri: String,
        direction: MessageDirection,
        sender: String,
        content: String,
    ) {
        try {
            repository.save(
                MessageLog(
                    provenanceUri = provenanceUri,
                    direction = direction,
                    sender = sender,
                    content = content,
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to log {} message for {}: {}", direction, provenanceUri, e.message)
        }
    }
}
