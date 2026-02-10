/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.OperationService
import com.enigmastation.streampack.irc.entity.IrcMessage
import com.enigmastation.streampack.irc.model.IrcMessageType
import com.enigmastation.streampack.irc.repository.IrcChannelRepository
import com.enigmastation.streampack.irc.repository.IrcMessageRepository
import com.enigmastation.streampack.irc.repository.IrcNetworkRepository
import java.util.concurrent.ConcurrentHashMap
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent
import org.kitteh.irc.client.library.event.channel.RequestedChannelJoinCompleteEvent
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder

/**
 * Manages a single Kitteh IRC client for one network. Not a Spring bean -- created dynamically by
 * IrcConnectionManager.
 */
class IrcAdapter(
    val networkName: String,
    private val eventGateway: EventGateway,
    private val operationService: OperationService,
    private val networkRepository: IrcNetworkRepository,
    private val channelRepository: IrcChannelRepository,
    private val messageRepository: IrcMessageRepository,
    private val client: Client,
    private val signalCharacter: String,
) {
    private val logger = LoggerFactory.getLogger(IrcAdapter::class.java)
    private val mutedChannels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val loggedChannels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        client.eventManager.registerEventListener(this)
    }

    fun connect() {
        client.connect()
    }

    fun disconnect() {
        client.shutdown("Disconnecting")
    }

    fun joinChannel(channelName: String) {
        client.addChannel(channelName)
    }

    fun leaveChannel(channelName: String) {
        client.removeChannel(channelName)
    }

    fun muteChannel(channelName: String) {
        mutedChannels.add(channelName.lowercase())
    }

    fun unmuteChannel(channelName: String) {
        mutedChannels.remove(channelName.lowercase())
    }

    fun isMuted(channelName: String): Boolean = channelName.lowercase() in mutedChannels

    fun isLogged(channelName: String): Boolean = channelName.lowercase() in loggedChannels

    fun setLogged(channelName: String, logged: Boolean) {
        if (logged) {
            loggedChannels.add(channelName.lowercase())
        } else {
            loggedChannels.remove(channelName.lowercase())
        }
    }

    /** Returns channels the client has joined */
    fun getJoinedChannels(): Set<String> = client.channels.map { it.name }.toSet()

    @Handler
    fun onConnected(event: ClientNegotiationCompleteEvent) {
        logger.info("Connected to network '{}'", networkName)
        val network = networkRepository.findByNameAndDeletedFalse(networkName) ?: return
        val autojoinChannels =
            channelRepository.findByNetworkAndAutojoinTrueAndDeletedFalse(network)
        for (channel in autojoinChannels) {
            logger.info("Auto-joining {} on {}", channel.name, networkName)
            client.addChannel(channel.name)
            if (channel.automute) {
                mutedChannels.add(channel.name.lowercase())
            }
            if (channel.logged) {
                loggedChannels.add(channel.name.lowercase())
            }
        }
    }

    @Handler
    fun onChannelMessage(event: ChannelMessageEvent) {
        Thread.startVirtualThread {
            try {
                val channelName = event.channel.name
                logMessage(channelName, event.actor.nick, event.message, IrcMessageType.MESSAGE)

                val provenance =
                    Provenance(
                        protocol = Protocol.IRC,
                        serviceId = networkName,
                        replyTo = channelName,
                    )

                val strippedText = extractAddressedText(event.message)
                if (strippedText != null) {
                    dispatchAndReply(strippedText, channelName, provenance)
                } else {
                    val preMessage =
                        MessageBuilder.withPayload(event.message)
                            .setHeader(Provenance.HEADER, provenance)
                            .build()
                    if (operationService.hasUnaddressedInterest(preMessage)) {
                        dispatchAndReply(event.message, channelName, provenance)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing channel message on {}: {}", networkName, e.message)
            }
        }
    }

    /** Sends payload through the EventGateway and replies to the channel if not muted */
    private fun dispatchAndReply(payload: String, channelName: String, provenance: Provenance) {
        val message =
            MessageBuilder.withPayload(payload).setHeader(Provenance.HEADER, provenance).build()
        val result = eventGateway.process(message)

        if (!isMuted(channelName)) {
            when (result) {
                is OperationResult.Success ->
                    client.sendMessage(channelName, result.payload.toString())
                is OperationResult.Error ->
                    client.sendMessage(channelName, "Error: ${result.message}")
                is OperationResult.NotHandled -> {}
            }
        }
    }

    /**
     * Detects whether a message is explicitly addressed to the bot. Returns the stripped payload
     * (no signal char or nick prefix) if addressed, or null if the message is not addressed.
     */
    private fun extractAddressedText(raw: String): String? {
        if (signalCharacter.isNotEmpty() && raw.startsWith(signalCharacter)) {
            val stripped = raw.removePrefix(signalCharacter).trimStart()
            return stripped.ifEmpty { null }
        }

        val nick = client.nick.lowercase()
        val lowerRaw = raw.lowercase()
        for (separator in listOf(": ", ", ")) {
            val prefix = "$nick$separator"
            if (lowerRaw.startsWith(prefix)) {
                val stripped = raw.substring(prefix.length).trimStart()
                return stripped.ifEmpty { null }
            }
        }

        return null
    }

    @Handler
    fun onPrivateMessage(event: PrivateMessageEvent) {
        Thread.startVirtualThread {
            try {
                val provenance =
                    Provenance(
                        protocol = Protocol.IRC,
                        serviceId = networkName,
                        replyTo = event.actor.nick,
                    )
                val message =
                    MessageBuilder.withPayload(event.message)
                        .setHeader(Provenance.HEADER, provenance)
                        .build()
                val result = eventGateway.process(message)

                when (result) {
                    is OperationResult.Success ->
                        client.sendMessage(event.actor.nick, result.payload.toString())
                    is OperationResult.Error ->
                        client.sendMessage(event.actor.nick, "Error: ${result.message}")
                    is OperationResult.NotHandled -> {}
                }
            } catch (e: Exception) {
                logger.error("Error processing private message on {}: {}", networkName, e.message)
            }
        }
    }

    @Handler
    fun onChannelAction(event: ChannelCtcpEvent) {
        logMessage(event.channel.name, event.actor.nick, event.message, IrcMessageType.ACTION)
    }

    @Handler
    fun onChannelJoined(event: RequestedChannelJoinCompleteEvent) {
        logger.info("Joined {} on {}", event.channel.name, networkName)
    }

    @Handler
    fun onChannelTopic(event: ChannelTopicEvent) {
        val newTopic = event.newTopic.value.orElse("")
        logMessage(event.channel.name, "system", newTopic, IrcMessageType.TOPIC)
    }

    @Handler
    fun onUserPart(event: ChannelPartEvent) {
        logMessage(event.channel.name, event.actor.nick, "left the channel", IrcMessageType.PART)
    }

    /** Persists a message to the log if logging is enabled for the channel */
    private fun logMessage(
        channelName: String,
        nick: String,
        content: String,
        messageType: IrcMessageType,
    ) {
        if (!isLogged(channelName)) return
        try {
            val network = networkRepository.findByNameAndDeletedFalse(networkName) ?: return
            val channel =
                channelRepository.findByNetworkAndNameAndDeletedFalse(network, channelName)
                    ?: return
            messageRepository.save(
                IrcMessage(
                    channel = channel,
                    nick = nick,
                    content = content,
                    messageType = messageType,
                )
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to log message for {} on {}: {}",
                channelName,
                networkName,
                e.message,
            )
        }
    }
}
