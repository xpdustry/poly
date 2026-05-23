// SPDX-License-Identifier: MIT
package com.xpdustry.poly

import arc.ApplicationListener
import arc.Core
import arc.net.ServerDiscoveryHandler
import dev.kord.common.entity.Snowflake
import dev.kord.common.kColor
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.AllowedMentionsBuilder
import dev.kord.rest.builder.message.embed
import java.awt.Color as AwtColor
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.gen.Call
import mindustry.mod.Plugin
import mindustry.net.Administration
import mindustry.net.Packets
import org.slf4j.LoggerFactory

class PolyPlugin : Plugin() {
    private val logger = LoggerFactory.getLogger(PolyPlugin::class.java)
    private val discordTokenConfig =
        Administration.Config("poly-discord-token", "The discord bot token.", "", ::onConfigured)
    private val statusChannelConfig =
        Administration.Config("poly-status-channel", "The status channel.", "", ::onConfigured)
    @Volatile private var state: PolyState = PolyState.None
    private var kord: Kord? = null
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, throwable ->
                    logger.error("An uncaught exception occurred in the coroutine scope of poly", throwable)
                }
        )

    override fun init() {
        val default = MindustryDiscovery.handler
        MindustryDiscovery.handler = ServerDiscoveryHandler { address, handler ->
            when (val state = this@PolyPlugin.state) {
                is PolyState.None -> default.onDiscoverReceived(address, handler)
                is PolyState.Claimed ->
                    try {
                        val result = runBlocking { suspendPingHost(state.address, state.port) }
                        result.port = Administration.Config.port.num()
                        handler.respond(toBuffer(result))
                    } catch (e: IOException) {
                        logger.error("Failed to proxy discovery response from {}:{}.", state.address, state.port, e)
                        throw e
                    }
            }
        }

        Core.app.addListener(
            object : ApplicationListener {
                override fun update() {
                    val state = this@PolyPlugin.state
                    if (state is PolyState.Claimed && state.expiresAt < Clock.System.now()) {
                        Vars.net.closeServer()
                        runBlocking { sendStatusMessage("Event server claim released.") }
                    }
                }

                override fun dispose() {
                    runBlocking { kord?.shutdown() }
                }
            }
        )

        Vars.net.handleServer(Packets.Connect::class.java) { con, _ ->
            when (val state = this@PolyPlugin.state) {
                is PolyState.Claimed -> Call.connect(con, state.address, state.port)
                is PolyState.None -> con.kick("This server leads to nowhere.", 0)
            }
        }

        this.onConfigured()
    }

    private fun onConfigured() {
        runBlocking { kord?.shutdown() }

        val token = discordTokenConfig.get().toString()
        if (token.isBlank()) {
            return
        }

        val kord: Kord
        try {
            kord = runBlocking { Kord(token) }
        } catch (e: Exception) {
            logger.error("Failed creating the discord bot", e)
            return
        }
        this.kord = kord
        logger.info("The discord bot has been created successfully.")

        scope.launch {
            kord.on<GuildChatInputCommandInteractionCreateEvent> {
                logger.debug("Received discord command {}.", interaction.command.rootName)
                var state = this@PolyPlugin.state
                when (interaction.command.rootName.lowercase()) {
                    "claim" -> {
                        if (state is PolyState.Claimed) {
                            interaction.respondPublic {
                                content = "The server is already claimed by ${state.claimer.mention}"
                                allowedMentions = noMentions()
                            }
                            return@on
                        }

                        val address = interaction.command.strings["address"]!!
                        val port = interaction.command.integers["port"]?.toInt() ?: Vars.port
                        val response = interaction.deferPublicResponse()
                        val host =
                            try {
                                suspendPingHost(address, port)
                            } catch (e: Exception) {
                                val message = "Failed to ping host from address $address"
                                logger.error(message, e)
                                response.respond { content = message }
                                return@on
                            }

                        val hours = (interaction.command.integers["hours"]?.toInt() ?: 24).hours
                        state = PolyState.Claimed(interaction.user, address, host.port, Clock.System.now() + hours)
                        Core.app.post { Vars.netServer.openServer() }
                        sendStatusMessage(
                            "Event server claimed by ${state.claimer.mention} for `$address:$port` until <t:${state.expiresAt.epochSeconds}:R>."
                        )
                        this@PolyPlugin.state = state
                        response.respond { content = "It is done..." }
                    }
                    "free" -> {
                        this@PolyPlugin.state = PolyState.None
                        Core.app.post { Vars.net.closeServer() }
                        sendStatusMessage("Event server claim released by ${interaction.user.mention}.")
                        interaction.respondPublic { content = "Released the event server claim." }
                    }
                    "status" -> {
                        interaction.respondPublic {
                            val state = this@PolyPlugin.state
                            content =
                                when (state) {
                                    is PolyState.Claimed -> "The server is claimed by ${state.claimer.mention}"
                                    is PolyState.None -> "No one claimed this server."
                                }
                        }
                    }
                    else -> {
                        interaction.respondPublic { content = "Unknown command." }
                    }
                }
            }

            kord.guilds.collect { guild ->
                logger.debug("Registering discord commands for guild {}.", guild.id)
                kord.createGuildChatInputCommand(guild.id, "claim", "Claim the event server.") {
                    string("address", "The target server address to redirect to") { required = true }
                    integer("port", "The target server port") {
                        required = false
                        minValue = 1
                        maxValue = 65535
                    }
                    integer("hours", "The claim duration in hours, maximum 24") {
                        required = false
                        minValue = 1
                        maxValue = 24
                    }
                }
                kord.createGuildChatInputCommand(guild.id, "status", "Shows the current status of the event server.")
                kord.createGuildChatInputCommand(guild.id, "free", "Release the claim on this event server.")
            }

            kord.login()
        }
    }

    suspend fun sendStatusMessage(message: String) {
        val channelId = statusChannelConfig.get().toString().toLongOrNull()
        val kord = this.kord ?: return
        if (channelId == null || channelId == -1L || kord.getChannel(Snowflake(channelId)) == null) return
        kord.rest.channel.createMessage(Snowflake(channelId.toULong())) {
            embed {
                description = message
                color = AwtColor.GREEN.kColor
            }
        }
    }

    private fun noMentions() = AllowedMentionsBuilder()

    sealed interface PolyState {
        data object None : PolyState

        data class Claimed(val claimer: Member, val address: String, val port: Int, val expiresAt: Instant) : PolyState
    }
}
