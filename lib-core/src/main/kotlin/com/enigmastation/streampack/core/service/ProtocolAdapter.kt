/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance

/**
 * Common contract for all protocol adapters (IRC, Slack, Discord, etc.).
 *
 * Each adapter knows how to detect whether outgoing text would be re-ingested as a new command
 * (loop detection) and how to deliver a reply to a provenance-identified destination.
 */
interface ProtocolAdapter {
    val protocol: Protocol
    val serviceName: String

    /**
     * Returns true if the given text would be re-ingested as an addressed command by this adapter.
     * Egress subscribers check this before sending to prevent command loops (e.g., a factoid whose
     * value starts with the signal character).
     */
    fun wouldTriggerIngress(text: String): Boolean

    /** Delivers a text reply to the destination identified by the given provenance */
    fun sendReply(provenance: Provenance, text: String)
}
