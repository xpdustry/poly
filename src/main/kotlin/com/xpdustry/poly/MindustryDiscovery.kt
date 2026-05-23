// SPDX-License-Identifier: MIT
package com.xpdustry.poly

import arc.net.Server
import arc.net.ServerDiscoveryHandler
import mindustry.Vars
import mindustry.net.ArcNetProvider
import mindustry.net.Net

object MindustryDiscovery {
    private val netProviderField = Net::class.java.getDeclaredField("provider").apply { isAccessible = true }
    private val arcNetProviderServerField =
        ArcNetProvider::class.java.getDeclaredField("server").apply { isAccessible = true }
    private val discoveryHandlerField =
        Server::class.java.getDeclaredField("discoveryHandler").apply { isAccessible = true }

    var handler: ServerDiscoveryHandler
        get() = server.discoveryHandler()
        set(value) = server.setDiscoveryHandler(value)

    private val server: Server
        get() = arcNetProviderServerField.get(netProviderField.get(Vars.net) as ArcNetProvider) as Server

    private fun Server.discoveryHandler() = discoveryHandlerField.get(this) as ServerDiscoveryHandler
}
