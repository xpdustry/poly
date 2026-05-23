// SPDX-License-Identifier: MIT
package com.xpdustry.poly

import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import mindustry.Vars
import mindustry.game.Gamemode
import mindustry.net.Host

data class MutableHost(
    val serverName: String,
    val mapName: String,
    val players: Int,
    val wave: Int,
    val version: Int,
    val versionType: String,
    val gameMode: Gamemode,
    val playerLimit: Int,
    val description: String,
    val gameModeName: String,
    var port: Int,
)

suspend fun suspendPingHost(address: String, port: Int): MutableHost {
    val mindustryHost =
        suspendCancellableCoroutine<Host> { continuation ->
            Vars.net.pingHost(address, port, continuation::resume, continuation::resumeWithException)
        }
    return MutableHost(
        serverName = mindustryHost.name,
        mapName = mindustryHost.mapname,
        players = mindustryHost.players,
        wave = mindustryHost.wave,
        version = mindustryHost.version,
        versionType = mindustryHost.versionType,
        gameMode = mindustryHost.mode,
        playerLimit = mindustryHost.playerLimit,
        description = mindustryHost.description,
        gameModeName = mindustryHost.modeName ?: "",
        port = mindustryHost.port,
    )
}

/*
fun fromDatagram(buffer: ByteBuffer): MutableHost =
    MutableHost(
        serverName = readString(buffer),
        mapName = readString(buffer),
        players = buffer.getInt(),
        wave = buffer.getInt(),
        version = buffer.getInt(),
        versionType = readString(buffer),
        gameMode =
            run {
                val index = buffer.get().toInt()
                if (index < 0 || index > Gamemode.all.size) Gamemode.survival else Gamemode.all[index]
            },
        playerLimit = buffer.getInt(),
        description = readString(buffer),
        gameModeName = readString(buffer),
        port =
            run {
                val port = buffer.getShort().toInt()
                if (port < 0) Vars.port else port
            },
    )

 */

fun toBuffer(host: MutableHost): ByteBuffer {
    val buffer = ByteBuffer.allocate(500)

    writeString(buffer, host.serverName, 100)
    writeString(buffer, host.mapName, 64)

    buffer.putInt(host.players)
    buffer.putInt(host.wave)
    buffer.putInt(host.version)
    writeString(buffer, host.versionType)

    buffer.put(host.gameMode.ordinal.toByte())
    buffer.putInt(host.playerLimit)

    writeString(buffer, host.description, 100)
    writeString(buffer, host.gameModeName, 50)
    buffer.putShort(host.port.toShort())

    val length = buffer.position()
    buffer.position(0)
    buffer.limit(length)
    return buffer
}

private fun writeString(buffer: ByteBuffer, string: String, maxLen: Int = 32) {
    var bytes = string.toByteArray(Vars.charset)
    if (bytes.size > maxLen) {
        bytes = bytes.sliceArray(0 until maxLen)
    }
    buffer.put(bytes.size.toByte())
    buffer.put(bytes)
}

private fun readString(buffer: ByteBuffer): String {
    val length = (buffer.get().toInt() and 0xff)
    val bytes = ByteArray(length)
    buffer.get(bytes)
    return String(bytes, Vars.charset)
}
