/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.integration

import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.MessageLogService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

/** Captures all inbound messages flowing through the ingress channel to the message log */
@Component
class IngressLoggingInterceptor(private val messageLogService: MessageLogService) :
    ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return message
        val sender = provenance.user?.username ?: message.headers["nick"] as? String ?: "unknown"
        val content = message.payload.toString()
        messageLogService.logInbound(provenance.encode(), sender, content)
        return message
    }
}
