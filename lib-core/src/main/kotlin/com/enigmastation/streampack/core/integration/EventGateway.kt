/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.integration

import com.enigmastation.streampack.core.model.OperationResult
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.messaging.Message

/**
 * Entry point for sending messages into the event system.
 *
 * Services use this gateway to submit messages for processing. The gateway sends the message to the
 * ingress channel, where the [com.enigmastation.streampack.core.service.OperationService] picks it
 * up and runs it through the operation chain. The result is returned synchronously to the caller.
 *
 * With virtual threads (Java 21+), the calling thread parks cheaply while waiting for the result,
 * so this scales well even under high concurrency.
 *
 * ## Usage
 *
 * ```
 * val message = MessageBuilder.withPayload(content)
 *     .setHeader(Provenance.HEADER, provenance)
 *     .build()
 * val result = eventGateway.process(message)
 * ```
 */
@MessagingGateway
interface EventGateway {
    @Gateway(requestChannel = "ingressChannel") fun process(message: Message<*>): OperationResult
}
