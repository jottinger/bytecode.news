/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.service.OperationService
import com.enigmastation.streampack.core.service.UserResolutionService
import com.enigmastation.streampack.irc.config.IrcProperties
import com.enigmastation.streampack.irc.entity.IrcNetwork
import com.enigmastation.streampack.irc.repository.IrcChannelRepository
import com.enigmastation.streampack.irc.repository.IrcMessageRepository
import com.enigmastation.streampack.irc.repository.IrcNetworkRepository
import java.util.concurrent.ConcurrentHashMap
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.feature.auth.SaslPlain
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Manages active IRC connections. Only instantiated when streampack.irc.enabled=true. Reads
 * autoconnect networks on startup and maintains a live adapter per connected network.
 */
@Component
@ConditionalOnProperty("streampack.irc.enabled", havingValue = "true")
class IrcConnectionManager(
    private val eventGateway: EventGateway,
    private val operationService: OperationService,
    private val userResolutionService: UserResolutionService,
    private val ircProperties: IrcProperties,
    private val networkRepository: IrcNetworkRepository,
    private val channelRepository: IrcChannelRepository,
    private val messageRepository: IrcMessageRepository,
) : InitializingBean, DisposableBean {
    private val logger = LoggerFactory.getLogger(IrcConnectionManager::class.java)
    private val adapters = ConcurrentHashMap<String, IrcAdapter>()

    override fun afterPropertiesSet() {
        val autoconnectNetworks = networkRepository.findByAutoconnectTrueAndDeletedFalse()
        for (network in autoconnectNetworks) {
            logger.info("Auto-connecting to network '{}'", network.name)
            connect(network)
        }
    }

    override fun destroy() {
        logger.info("Shutting down all IRC connections")
        for ((name, adapter) in adapters) {
            logger.info("Disconnecting from '{}'", name)
            adapter.disconnect()
        }
        adapters.clear()
    }

    /** Builds a Kitteh Client and connects to the given network */
    fun connect(network: IrcNetwork) {
        if (adapters.containsKey(network.name)) {
            logger.warn("Already connected to network '{}'", network.name)
            return
        }

        val securityType =
            if (network.tls) Client.Builder.Server.SecurityType.SECURE
            else Client.Builder.Server.SecurityType.INSECURE

        val client =
            Client.builder()
                .nick(network.nick)
                .user(network.nick)
                .server()
                .host(network.host)
                .port(network.port, securityType)
                .then()
                .build()

        val account = network.saslAccount
        val password = network.saslPassword
        if (account != null && password != null) {
            client.authManager.addProtocol(SaslPlain(client, account, password))
        }

        val effectiveSignal = network.signalCharacter ?: ircProperties.signalCharacter
        val adapter =
            IrcAdapter(
                networkName = network.name,
                eventGateway = eventGateway,
                operationService = operationService,
                userResolutionService = userResolutionService,
                networkRepository = networkRepository,
                channelRepository = channelRepository,
                messageRepository = messageRepository,
                client = client,
                signalCharacter = effectiveSignal,
            )
        adapters[network.name] = adapter
        adapter.connect()
    }

    fun disconnect(networkName: String) {
        val adapter = adapters.remove(networkName)
        if (adapter != null) {
            adapter.disconnect()
            logger.info("Disconnected from '{}'", networkName)
        }
    }

    fun join(networkName: String, channelName: String) {
        adapters[networkName]?.joinChannel(channelName)
    }

    fun leave(networkName: String, channelName: String) {
        adapters[networkName]?.leaveChannel(channelName)
    }

    fun mute(networkName: String, channelName: String) {
        adapters[networkName]?.muteChannel(channelName)
    }

    fun unmute(networkName: String, channelName: String) {
        adapters[networkName]?.unmuteChannel(channelName)
    }

    /** Returns status summary for a specific network or all networks */
    fun getStatus(networkName: String?): String {
        if (networkName != null) {
            val adapter = adapters[networkName]
            return if (adapter != null) {
                val channels = adapter.getJoinedChannels()
                val mutedList =
                    channels.filter { adapter.isMuted(it) }.joinToString(", ").ifEmpty { "none" }
                "Network '$networkName': connected, channels=${channels.joinToString(", ")}, muted=$mutedList"
            } else {
                "Network '$networkName': not connected"
            }
        }

        if (adapters.isEmpty()) {
            return "No active IRC connections"
        }

        return adapters.entries.joinToString("\n") { (name, adapter) ->
            val channels = adapter.getJoinedChannels()
            "  $name: ${channels.size} channel(s) [${channels.joinToString(", ")}]"
        }
    }

    /** Returns the adapter for the given network, or null if not connected */
    fun getAdapter(networkName: String): IrcAdapter? = adapters[networkName]

    fun isConnected(networkName: String): Boolean = adapters.containsKey(networkName)
}
