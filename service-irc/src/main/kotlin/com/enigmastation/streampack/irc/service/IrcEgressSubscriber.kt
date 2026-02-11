/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.core.integration.EgressSubscriber
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ChannelControlService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Watches the egress channel for IRC-bound results and routes them through the appropriate network
 * adapter. Muted channels are silenced here -- the message was still processed by operations, but
 * the reply is suppressed.
 */
@Component
@ConditionalOnProperty("streampack.irc.enabled", havingValue = "true")
class IrcEgressSubscriber(
    private val connectionManager: IrcConnectionManager,
    private val channelControlService: ChannelControlService,
) : EgressSubscriber() {
    private val logger = LoggerFactory.getLogger(IrcEgressSubscriber::class.java)

    override fun matches(provenance: Provenance): Boolean = provenance.protocol == Protocol.IRC

    override fun deliver(result: OperationResult, provenance: Provenance) {
        val networkName = provenance.serviceId
        if (networkName == null) {
            logger.warn("IRC egress message has no serviceId, dropping")
            return
        }
        val adapter = connectionManager.getAdapter(networkName)
        if (adapter == null) {
            logger.warn("No adapter for network '{}', dropping egress message", networkName)
            return
        }

        val isMuted = channelControlService.getOptions(provenance.encode())?.automute ?: false
        if (isMuted) {
            logger.debug(
                "Channel '{}' is muted on '{}', suppressing reply",
                provenance.replyTo,
                networkName,
            )
            return
        }

        when (result) {
            is OperationResult.Success ->
                adapter.sendMessage(provenance.replyTo, result.payload.toString())
            is OperationResult.Error ->
                adapter.sendMessage(provenance.replyTo, "Error: ${result.message}")
            is OperationResult.NotHandled -> {}
        }
    }
}
