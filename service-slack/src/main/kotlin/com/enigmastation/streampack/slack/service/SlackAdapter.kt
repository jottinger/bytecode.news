/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.slack.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ChannelControlService
import com.enigmastation.streampack.core.service.ProtocolAdapter
import com.enigmastation.streampack.core.service.UserResolutionService
import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp
import com.slack.api.methods.MethodsClient
import com.slack.api.model.event.MessageEvent
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder

/**
 * Manages a single Slack workspace connection via Socket Mode. Not a Spring bean -- created
 * dynamically by SlackConnectionManager.
 */
class SlackAdapter(
    val workspaceName: String,
    private val botToken: String,
    private val appToken: String,
    private val signalCharacter: String,
    private val eventGateway: EventGateway,
    private val userResolutionService: UserResolutionService,
    private val channelControlService: ChannelControlService,
) : ProtocolAdapter {
    override val protocol: Protocol = Protocol.SLACK
    override val serviceName: String = workspaceName
    private val logger = LoggerFactory.getLogger(SlackAdapter::class.java)
    private lateinit var boltApp: App
    private lateinit var socketModeApp: SocketModeApp
    private var botUserId: String? = null
    private val displayNameCache = ConcurrentHashMap<String, String>()

    /** Creates the Bolt app, registers event handlers, and starts the Socket Mode connection */
    fun connect() {
        val config = AppConfig.builder().singleTeamBotToken(botToken).build()
        boltApp = App(config)

        boltApp.event(MessageEvent::class.java) { payload, ctx ->
            val event = payload.event
            Thread.startVirtualThread { handleMessage(event) }
            ctx.ack()
        }

        socketModeApp = SocketModeApp(appToken, boltApp)
        socketModeApp.startAsync()

        resolveBotUserId()
        logger.info("Connected to Slack workspace '{}'", workspaceName)
    }

    /** Stops the Socket Mode connection and shuts down the Bolt app */
    fun disconnect() {
        if (::socketModeApp.isInitialized) {
            socketModeApp.close()
        }
        logger.info("Disconnected from Slack workspace '{}'", workspaceName)
    }

    /** Sends a message to a Slack channel by channel ID */
    fun sendMessage(channelId: String, text: String) {
        try {
            val client = methodsClient()
            client.chatPostMessage { r -> r.channel(channelId).text(text) }
        } catch (e: Exception) {
            logger.error(
                "Failed to send message to {} on '{}': {}",
                channelId,
                workspaceName,
                e.message,
            )
        }
    }

    /**
     * Resolves a channel name (e.g., "#general") to a Slack channel ID (e.g., "C0123456789") via
     * the conversations.list API. Returns null if the channel is not found.
     */
    fun resolveChannelId(channelName: String): String? {
        val cleanName = channelName.removePrefix("#")
        try {
            val client = methodsClient()
            var cursor: String? = null
            do {
                val response =
                    client.conversationsList { r ->
                        r.limit(200)
                        if (cursor != null) r.cursor(cursor)
                        r
                    }
                if (!response.isOk) {
                    logger.warn(
                        "conversations.list failed on '{}': {}",
                        workspaceName,
                        response.error,
                    )
                    return null
                }
                for (channel in response.channels) {
                    if (channel.name == cleanName) {
                        return channel.id
                    }
                }
                cursor = response.responseMetadata?.nextCursor
            } while (!cursor.isNullOrEmpty())
        } catch (e: Exception) {
            logger.error(
                "Failed to resolve channel '{}' on '{}': {}",
                channelName,
                workspaceName,
                e.message,
            )
        }
        return null
    }

    /** Returns an authenticated Slack API client using the bot token */
    private fun methodsClient(): MethodsClient = boltApp.slack.methods(botToken)

    /** Resolves a Slack user ID to a display name, caching results */
    private fun resolveDisplayName(slackUserId: String): String {
        return displayNameCache.getOrPut(slackUserId) {
            try {
                val response = methodsClient().usersInfo { r -> r.user(slackUserId) }
                if (response.isOk) {
                    val profile = response.user.profile
                    profile.displayName?.ifBlank { null }
                        ?: profile.realName?.ifBlank { null }
                        ?: response.user.name
                        ?: slackUserId
                } else {
                    logger.warn(
                        "users.info failed for {} on '{}': {}",
                        slackUserId,
                        workspaceName,
                        response.error,
                    )
                    slackUserId
                }
            } catch (e: Exception) {
                logger.warn(
                    "Could not resolve display name for {} on '{}': {}",
                    slackUserId,
                    workspaceName,
                    e.message,
                )
                slackUserId
            }
        }
    }

    /** Resolves the bot's own user ID for mention detection */
    private fun resolveBotUserId() {
        try {
            val response = methodsClient().authTest { it }
            if (response.isOk) {
                botUserId = response.userId
                logger.info("Bot user ID for '{}': {}", workspaceName, botUserId)
            } else {
                logger.warn("authTest failed for '{}': {}", workspaceName, response.error)
            }
        } catch (e: Exception) {
            logger.warn("Could not resolve bot user ID for '{}': {}", workspaceName, e.message)
        }
    }

    /** Processes an incoming Slack message event */
    private fun handleMessage(event: MessageEvent) {
        try {
            logger.debug(
                "Slack event on '{}': user={}, channel={}, botId={}, subtype={}, text={}",
                workspaceName,
                event.user,
                event.channel,
                event.botId,
                event.subtype,
                event.text?.take(80),
            )

            // Skip bot messages to avoid self-loops
            if (event.botId != null) return
            // Skip message subtypes (edits, deletes, joins, etc.)
            if (event.subtype != null) return

            val slackUserId = event.user ?: return
            val channelId = event.channel ?: return
            val rawText = event.text ?: return

            val user = userResolutionService.resolve(Protocol.SLACK, workspaceName, slackUserId)
            val isDm = event.channelType == "im"

            val replyTo = if (isDm) slackUserId else channelId
            val provenance =
                Provenance(
                    protocol = Protocol.SLACK,
                    serviceId = workspaceName,
                    replyTo = replyTo,
                    user = user,
                    metadata =
                        buildMap {
                            put("channelId", channelId)
                            botUserId?.let { put(Provenance.BOT_NICK, it) }
                        },
                )

            val nick = resolveDisplayName(slackUserId)
            val addressedText = if (isDm) rawText else extractAddressedText(rawText)
            val isAddressed = isDm || addressedText != null

            dispatch(addressedText ?: rawText, provenance, isAddressed, nick)
        } catch (e: Exception) {
            logger.error("Error processing Slack message on '{}': {}", workspaceName, e.message)
        }
    }

    /**
     * Detects whether a message is explicitly addressed to the bot. Returns the stripped payload if
     * addressed, or null if not addressed.
     */
    private fun extractAddressedText(raw: String): String? {
        // Signal character prefix
        if (signalCharacter.isNotEmpty() && raw.startsWith(signalCharacter)) {
            val stripped = raw.removePrefix(signalCharacter).trimStart()
            return stripped.ifEmpty { null }
        }

        // Bot @mention prefix (Slack formats mentions as <@U1234567>)
        val userId = botUserId
        if (userId != null) {
            val mentionPrefix = "<@$userId>"
            if (raw.startsWith(mentionPrefix)) {
                val stripped = raw.removePrefix(mentionPrefix).trimStart()
                return stripped.ifEmpty { null }
            }
        }

        return null
    }

    /** Sends payload through the EventGateway as fire-and-forget */
    private fun dispatch(
        payload: String,
        provenance: Provenance,
        addressed: Boolean,
        nick: String? = null,
    ) {
        val builder =
            MessageBuilder.withPayload(payload as Any)
                .setHeader(Provenance.HEADER, provenance)
                .setHeader(Provenance.ADDRESSED, addressed)
        if (nick != null) builder.setHeader("nick", nick)
        eventGateway.send(builder.build())
    }
}
