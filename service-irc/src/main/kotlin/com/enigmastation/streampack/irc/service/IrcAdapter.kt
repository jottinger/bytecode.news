/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.LoggingRequest
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ChannelControlService
import com.enigmastation.streampack.core.service.UserResolutionService
import com.enigmastation.streampack.irc.repository.IrcChannelRepository
import com.enigmastation.streampack.irc.repository.IrcNetworkRepository
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent
import org.kitteh.irc.client.library.event.channel.RequestedChannelJoinCompleteEvent
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent
import org.kitteh.irc.client.library.event.user.UserQuitEvent
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder

/**
 * Manages a single Kitteh IRC client for one network. Not a Spring bean -- created dynamically by
 * IrcConnectionManager.
 */
class IrcAdapter(
    val networkName: String,
    private val eventGateway: EventGateway,
    private val userResolutionService: UserResolutionService,
    private val channelControlService: ChannelControlService,
    private val networkRepository: IrcNetworkRepository,
    private val channelRepository: IrcChannelRepository,
    private val client: Client,
    private val signalCharacter: String,
) {
    private val logger = LoggerFactory.getLogger(IrcAdapter::class.java)

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

    /** Sends a message to the given target (channel or nick) via the IRC client */
    fun sendMessage(target: String, text: String) {
        client.sendMessage(target, text)
    }

    /** Returns channels the client has joined */
    fun getJoinedChannels(): Set<String> = client.channels.map { it.name }.toSet()

    @Handler
    fun onConnected(event: ClientNegotiationCompleteEvent) {
        logger.info("Connected to network '{}'", networkName)
        val network = networkRepository.findByNameAndDeletedFalse(networkName) ?: return
        val channels = channelRepository.findByNetworkAndDeletedFalse(network)
        for (channel in channels) {
            val options = channelControlService.getOptions(channel.provenanceUri())
            if (options?.autojoin == true) {
                logger.info("Auto-joining {} on {}", channel.name, networkName)
                client.addChannel(channel.name)
            }
        }
    }

    @Handler
    fun onChannelMessage(event: ChannelMessageEvent) {
        Thread.startVirtualThread {
            try {
                val channelName = event.channel.name
                val nick = event.actor.nick
                val user = userResolutionService.resolve(Protocol.IRC, networkName, nick)
                val provenance =
                    Provenance(
                        protocol = Protocol.IRC,
                        serviceId = networkName,
                        replyTo = channelName,
                        user = user,
                        metadata = mapOf(Provenance.BOT_NICK to client.nick),
                    )
                val host = event.actor.host
                val ident = event.actor.userString

                val strippedText = extractAddressedText(event.message)
                if (strippedText != null) {
                    dispatch(strippedText, provenance, nick, host, ident)
                } else {
                    dispatch(event.message, provenance, nick, host, ident)
                }
            } catch (e: Exception) {
                logger.error("Error processing channel message on {}: {}", networkName, e.message)
            }
        }
    }

    /** Sends payload through the EventGateway as fire-and-forget; results arrive via egress */
    private fun dispatch(
        payload: String,
        provenance: Provenance,
        nick: String? = null,
        host: String? = null,
        ident: String? = null,
    ) {
        val builder = MessageBuilder.withPayload(payload).setHeader(Provenance.HEADER, provenance)
        if (nick != null) builder.setHeader("nick", nick)
        if (host != null) builder.setHeader("host", host)
        if (ident != null) builder.setHeader("ident", ident)
        eventGateway.send(builder.build())
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
                val nick = event.actor.nick
                val user = userResolutionService.resolve(Protocol.IRC, networkName, nick)
                val provenance =
                    Provenance(
                        protocol = Protocol.IRC,
                        serviceId = networkName,
                        replyTo = nick,
                        user = user,
                        metadata = mapOf(Provenance.BOT_NICK to client.nick),
                    )
                dispatch(event.message, provenance, nick, event.actor.host, event.actor.userString)
            } catch (e: Exception) {
                logger.error("Error processing private message on {}: {}", networkName, e.message)
            }
        }
    }

    @Handler
    fun onChannelAction(event: ChannelCtcpEvent) {
        if (!event.message.startsWith("ACTION ")) return
        val action = event.message.removePrefix("ACTION ").removeSuffix("\u0001")
        dispatchLoggingEvent(event.channel.name, "* ${event.actor.nick} $action")
    }

    @Handler
    fun onChannelJoined(event: RequestedChannelJoinCompleteEvent) {
        logger.info("Joined {} on {}", event.channel.name, networkName)
    }

    @Handler
    fun onUserJoin(event: ChannelJoinEvent) {
        dispatchLoggingEvent(
            event.channel.name,
            "* ${event.actor.nick} joined ${event.channel.name}",
        )
    }

    @Handler
    fun onChannelTopic(event: ChannelTopicEvent) {
        val setter = event.newTopic.setter.map { it.name }.orElse("someone")
        val newTopic = event.newTopic.value.orElse("")
        dispatchLoggingEvent(event.channel.name, "* $setter changed the topic to: $newTopic")
    }

    @Handler
    fun onUserPart(event: ChannelPartEvent) {
        val reason = event.message.let { if (it.isNotEmpty()) " ($it)" else "" }
        dispatchLoggingEvent(
            event.channel.name,
            "* ${event.actor.nick} left ${event.channel.name}$reason",
        )
    }

    @Handler
    fun onNickChange(event: UserNickChangeEvent) {
        dispatchLoggingEvent("*", "* ${event.actor.nick} is now known as ${event.newUser.nick}")
    }

    @Handler
    fun onUserQuit(event: UserQuitEvent) {
        val reason = event.message.let { if (it.isNotEmpty()) " ($it)" else "" }
        dispatchLoggingEvent("*", "* ${event.actor.nick} quit$reason")
    }

    /** Dispatches a metadata event as a LoggingRequest through ingress for logging only */
    private fun dispatchLoggingEvent(channelName: String, content: String) {
        Thread.startVirtualThread {
            try {
                val provenance =
                    Provenance(
                        protocol = Protocol.IRC,
                        serviceId = networkName,
                        replyTo = channelName,
                        metadata = mapOf(Provenance.BOT_NICK to client.nick),
                    )
                val message =
                    MessageBuilder.withPayload(LoggingRequest(content) as Any)
                        .setHeader(Provenance.HEADER, provenance)
                        .build()
                eventGateway.send(message)
            } catch (e: Exception) {
                logger.error("Error dispatching logging event on {}: {}", networkName, e.message)
            }
        }
    }
}
