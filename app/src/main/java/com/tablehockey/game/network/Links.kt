package com.tablehockey.game.network

import org.json.JSONObject

/**
 * Transport-agnostic ends of a two-device match. WiFi (TCP + NSD) and
 * Bluetooth (RFCOMM) both speak the same line-delimited JSON protocol from
 * [NetCodec], so the game screen only ever sees these two interfaces.
 */
interface HostLink {
    interface Listener {
        fun onClientConnected()
        fun onClientDisconnected()
        fun onMessage(obj: JSONObject)
    }

    var listener: Listener?

    /** Snapshots per second this transport comfortably carries. */
    val stateHz: Int

    fun send(json: String)
    fun stop()
}

interface GuestLink {
    interface Listener {
        fun onConnected()
        fun onConnectFailed(reason: String)
        fun onDisconnected()
        fun onMessage(obj: JSONObject)
    }

    var listener: Listener?

    /** Input packets per second this transport comfortably carries. */
    val inputHz: Int

    fun send(json: String)
    fun disconnect()
}
