package com.tablehockey.game.network

/**
 * Holds the live host / guest link set up in the lobby so GameActivity can
 * pick it up. Sockets can't travel through an Intent, so this process-wide
 * singleton is the hand-off point between the two screens. Works for both
 * the WiFi and the Bluetooth transports.
 */
object NetworkSession {
    var host: HostLink? = null
    var guest: GuestLink? = null

    fun clear() {
        host?.stop()
        guest?.disconnect()
        host = null
        guest = null
    }
}
