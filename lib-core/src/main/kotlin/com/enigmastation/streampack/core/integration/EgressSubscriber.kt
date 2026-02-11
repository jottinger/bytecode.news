/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.integration

import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler

/**
 * Base class for services that watch the egress channel for operation results.
 *
 * Subclasses implement [matches] to declare which messages they claim (typically by checking the
 * provenance protocol) and [deliver] to handle the matched result. The infrastructure subscribes
 * all EgressSubscriber beans to the egress channel automatically.
 *
 * The subscriber is intentionally thin -- it filters and dispatches. All routing decisions (muting,
 * channel selection, network lookup) belong in the service, not here.
 */
abstract class EgressSubscriber : MessageHandler {

    /** Return true if this subscriber should receive messages with the given provenance */
    abstract fun matches(provenance: Provenance): Boolean

    /** Handle a matched operation result. Called only when [matches] returned true. */
    abstract fun deliver(result: OperationResult, provenance: Provenance)

    final override fun handleMessage(message: Message<*>) {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return
        val result = message.payload as? OperationResult ?: return
        if (matches(provenance)) {
            deliver(result, provenance)
        }
    }
}
