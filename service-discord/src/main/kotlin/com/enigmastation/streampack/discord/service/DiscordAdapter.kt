/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.discord.service

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ChannelControlService
import com.enigmastation.streampack.core.service.UserResolutionService
import com.enigmastation.streampack.discord.config.DiscordProperties
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.integration.support.MessageBuilder
import org.springframework.stereotype.Component

/** Manages the JDA connection lifecycle and dispatches Discord messages into the event gateway */
@Component
@ConditionalOnProperty("streampack.discord.enabled", havingValue = "true")
@EnableConfigurationProperties(DiscordProperties::class)
class DiscordAdapter(
    private val eventGateway: EventGateway,
    private val userResolutionService: UserResolutionService,
    private val channelControlService: ChannelControlService,
    private val properties: DiscordProperties,
) : ListenerAdapter(), InitializingBean, DisposableBean {

    private val logger = LoggerFactory.getLogger(DiscordAdapter::class.java)
    private lateinit var jda: JDA

    override fun afterPropertiesSet() {
        if (properties.token.isBlank()) {
            logger.error("Discord token is blank, cannot connect")
            return
        }
        logger.info("Connecting to Discord")
        jda =
            JDABuilder.createDefault(properties.token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build()
        jda.awaitReady()
        logger.info("Discord connection established")
    }

    override fun destroy() {
        if (::jda.isInitialized) {
            logger.info("Shutting down Discord connection")
            jda.shutdown()
        }
    }

    override fun onReady(event: ReadyEvent) {
        val selfUser = event.jda.selfUser
        logger.info("Discord bot ready as {}", selfUser.name)

        if (properties.applicationId.isNotBlank()) {
            logger.info(
                "Invite URL: https://discord.com/oauth2/authorize?client_id={}&scope=bot&permissions={}",
                properties.applicationId,
                properties.permissions,
            )
        }

        // Register channel control options for discovered guilds/channels
        for (guild in event.jda.guilds) {
            for (channel in guild.textChannels) {
                val uri =
                    Provenance(
                            protocol = Protocol.DISCORD,
                            serviceId = guild.id,
                            replyTo = "#${channel.name}",
                        )
                        .encode()
                channelControlService.getOrCreateOptions(uri)
            }
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        Thread.startVirtualThread {
            try {
                handleMessage(event)
            } catch (e: Exception) {
                logger.error("Error processing Discord message: {}", e.message)
            }
        }
    }

    private fun handleMessage(event: MessageReceivedEvent) {
        val rawText = event.message.contentRaw

        if (event.isFromGuild) {
            val guild = event.guild
            val channelName = "#${event.channel.name}"
            val user = userResolutionService.resolve(Protocol.DISCORD, guild.id, event.author.id)
            val provenance =
                Provenance(
                    protocol = Protocol.DISCORD,
                    serviceId = guild.id,
                    replyTo = channelName,
                    user = user,
                    metadata =
                        mapOf(
                            Provenance.BOT_NICK to event.jda.selfUser.name,
                            "guildName" to guild.name,
                        ),
                )

            val addressedText = extractAddressedText(rawText, event)
            val isAddressed = addressedText != null
            dispatch(addressedText ?: rawText, provenance, isAddressed)
        } else {
            // Direct message
            val user = userResolutionService.resolve(Protocol.DISCORD, "", event.author.id)
            val provenance =
                Provenance(
                    protocol = Protocol.DISCORD,
                    replyTo = event.author.id,
                    user = user,
                    metadata = mapOf(Provenance.BOT_NICK to event.jda.selfUser.name),
                )
            // DMs are always addressed
            dispatch(rawText, provenance, addressed = true)
        }
    }

    /** Detects whether a message is addressed to the bot via signal character or @mention */
    private fun extractAddressedText(raw: String, event: MessageReceivedEvent): String? {
        // Signal character prefix
        if (properties.signalCharacter.isNotEmpty() && raw.startsWith(properties.signalCharacter)) {
            val stripped = raw.removePrefix(properties.signalCharacter).trimStart()
            return stripped.ifEmpty { null }
        }

        // Bot @mention prefix
        val selfId = event.jda.selfUser.id
        val mentionPatterns = listOf("<@$selfId>", "<@!$selfId>")
        for (mention in mentionPatterns) {
            if (raw.startsWith(mention)) {
                val stripped = raw.removePrefix(mention).trimStart()
                return stripped.ifEmpty { null }
            }
        }

        return null
    }

    private fun dispatch(payload: String, provenance: Provenance, addressed: Boolean) {
        val message =
            MessageBuilder.withPayload(payload as Any)
                .setHeader(Provenance.HEADER, provenance)
                .setHeader(Provenance.ADDRESSED, addressed)
                .build()
        eventGateway.send(message)
    }

    /** Sends a text message to a guild channel */
    fun sendToChannel(guildId: String, channelName: String, text: String) {
        if (!::jda.isInitialized) {
            logger.warn("JDA not initialized, cannot send to {}/{}", guildId, channelName)
            return
        }
        val guild = jda.getGuildById(guildId)
        if (guild == null) {
            logger.warn("Guild '{}' not found", guildId)
            return
        }
        val cleanName = channelName.removePrefix("#")
        val channels = guild.getTextChannelsByName(cleanName, true)
        if (channels.isEmpty()) {
            logger.warn("Channel '{}' not found in guild '{}'", channelName, guild.name)
            return
        }
        channels.first().sendMessage(text).queue()
    }

    /** Sends a direct message to a user by their Discord user ID */
    fun sendPrivateMessage(userId: String, text: String) {
        if (!::jda.isInitialized) {
            logger.warn("JDA not initialized, cannot send DM to {}", userId)
            return
        }
        jda.openPrivateChannelById(userId).queue { channel -> channel.sendMessage(text).queue() }
    }
}
