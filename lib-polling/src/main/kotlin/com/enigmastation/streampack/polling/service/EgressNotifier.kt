/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.polling.service

import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Sends notifications to the egress channel with decoded provenance addressing */
@Component
class EgressNotifier(@Qualifier("egressChannel") private val egressChannel: MessageChannel) {
    private val logger = LoggerFactory.getLogger(EgressNotifier::class.java)

    /** Send a text notification to a destination identified by its provenance URI */
    fun send(text: String, destinationUri: String) {
        try {
            val provenance = Provenance.decode(destinationUri)
            val message =
                MessageBuilder.withPayload(OperationResult.Success(text) as Any)
                    .setHeader(Provenance.HEADER, provenance)
                    .build()
            egressChannel.send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send notification to {}: {}", destinationUri, e.message)
        }
    }
}
