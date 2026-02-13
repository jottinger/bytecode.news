/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.bridge.operation

import com.enigmastation.streampack.bridge.service.BridgeService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Cross-posts user messages to bridge copy targets. Runs at priority 1 (before all other
 * operations) and returns null so normal processing continues on the original message.
 */
@Component
class BridgeCopyOperation(
    private val bridgeService: BridgeService,
    @Qualifier("egressChannel") private val egressChannel: MessageChannel,
) : TypedOperation<String>(String::class) {

    override val priority: Int = 1
    override val addressed: Boolean = false

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return null
        val nick = message.headers["nick"] as? String ?: "unknown"

        // Skip already-bridged messages to prevent re-copy loops
        if (provenance.metadata.containsKey(BRIDGED_KEY)) return null

        val targets = bridgeService.getCopyTargets(provenance.encode())
        if (targets.isEmpty()) return null

        // Prefer display-resolved text (e.g. Discord mention resolution) over raw payload
        val displayText = message.headers[DISPLAY_TEXT_HEADER] as? String ?: payload
        val attributed = "<${provenance.encode()}/$nick> $displayText"
        for (targetUri in targets) {
            val targetProvenance = Provenance.decode(targetUri)
            val egressMessage =
                MessageBuilder.withPayload(OperationResult.Success(attributed) as Any)
                    .setHeader(
                        Provenance.HEADER,
                        targetProvenance.copy(
                            metadata = targetProvenance.metadata + (BRIDGED_KEY to true)
                        ),
                    )
                    .build()
            try {
                egressChannel.send(egressMessage)
            } catch (e: Exception) {
                logger.warn("Failed to bridge message to {}: {}", targetUri, e.message)
            }
        }

        // Return null so the original message continues through the operation chain
        return null
    }

    companion object {
        /** Metadata key marking a message as already bridged to prevent re-copy loops */
        const val BRIDGED_KEY = "streampack_bridged"

        /** Header containing display-resolved text for bridging (e.g. Discord mentions resolved) */
        const val DISPLAY_TEXT_HEADER = "displayText"
    }
}
