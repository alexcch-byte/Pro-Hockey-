package com.tablehockey.game.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Unambiguous alphabet for room codes -- no 0/O/1/I/L. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

private fun randomRoomCode(length: Int = 5): String =
    (1..length).map { CODE_ALPHABET.random() }.joinToString("")

/**
 * Converts an https:// relay base URL into the wss:// URL for a given room.
 * RELAY_BASE_URL is written as https:// since that's what you paste from
 * `wrangler deploy`'s output; OkHttp's WebSocket client wants ws(s)://.
 */
private fun roomSocketUrl(role: String, code: String): String {
    val wsBase = RELAY_BASE_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    return "$wsBase/room/$code?role=$role"
}

/**
 * Host side of an internet match, relayed through the Cloudflare Worker in
 * relay-server/ (see RELAY_BASE_URL). Speaks the same NetCodec
 * line-delimited JSON protocol as GameServer (WiFi) and BluetoothHost -- the
 * relay is a dumb pipe that only ever adds two control messages of its own
 * (_hello, _bye), so from GameView's point of view this is just another
 * HostLink.
 */
class RelayHost(private val roomCode: String = randomRoomCode()) : HostLink {

    override var listener: HostLink.Listener? = null
    override val stateHz: Int = 30

    /** The code to share with whoever is joining; valid as soon as this is constructed. */
    fun code(): String = roomCode

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(roomSocketUrl("host", roomCode)).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = try { JSONObject(text) } catch (_: Exception) { return }
                mainHandler.post { handle(obj) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post { listener?.onClientDisconnected() }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Relay connection failed: ${t.message}")
                mainHandler.post { listener?.onClientDisconnected() }
            }
        })
    }

    private fun handle(obj: JSONObject) {
        when (obj.optString("t")) {
            "_hello" -> listener?.onClientConnected()
            "_bye" -> listener?.onClientDisconnected()
            else -> listener?.onMessage(obj)
        }
    }

    override fun send(json: String) {
        socket?.send(json)
    }

    override fun stop() {
        socket?.close(1000, null)
        socket = null
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "RelayHost"
    }
}

/**
 * Guest side of an internet match: connects to whatever room code the host
 * is showing, over the same Cloudflare Worker relay RelayHost uses.
 */
class RelayGuest : GuestLink {

    override var listener: GuestLink.Listener? = null
    override val inputHz: Int = 30

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect(roomCode: String) {
        val request = Request.Builder().url(roomSocketUrl("guest", roomCode.uppercase())).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post { listener?.onConnected() }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = try { JSONObject(text) } catch (_: Exception) { return }
                mainHandler.post { handle(obj) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post { listener?.onDisconnected() }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Relay connection failed: ${t.message}")
                mainHandler.post { listener?.onConnectFailed(t.message ?: "Connection failed") }
            }
        })
    }

    private fun handle(obj: JSONObject) {
        when (obj.optString("t")) {
            "_bye" -> listener?.onDisconnected()
            "_hello" -> {} // only meaningful to the host side
            else -> listener?.onMessage(obj)
        }
    }

    override fun send(json: String) {
        socket?.send(json)
    }

    override fun disconnect() {
        socket?.close(1000, null)
        socket = null
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "RelayGuest"
    }
}
