package com.tablehockey.game.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredHost(val name: String, val address: InetAddress, val port: Int)

/**
 * Client side of a LAN match: discovers hosts via NSD (or connects to a
 * manually entered IP), sends local controller input and receives
 * authoritative state from the host.
 */
class GameClient(private val context: Context) : GuestLink {

    override var listener: GuestLink.Listener? = null
    override val inputHz: Int = 30

    /** Discovery results, separate from the link callbacks. */
    var onDiscoveryUpdated: ((List<DiscoveredHost>) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null

    private val nsdManager: NsdManager by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val discovered = mutableMapOf<String, DiscoveredHost>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun startDiscovery() {
        stopDiscovery()
        discovered.clear()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.startsWith(SERVICE_TYPE.trimEnd('.'))) {
                    enqueueResolve(info)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                discovered.remove(info.serviceName)
                mainHandler.post { onDiscoveryUpdated?.invoke(discovered.values.toList()) }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices error: ${e.message}")
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (!resolving) {
                resolving = true
                resolveNext()
            }
        }
    }

    private fun resolveNext() {
        val next: NsdServiceInfo?
        synchronized(resolveQueue) {
            next = resolveQueue.removeFirstOrNull()
            if (next == null) {
                resolving = false
                return
            }
        }
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                synchronized(resolveQueue) { resolveNext() }
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = DiscoveredHost(info.serviceName, info.host, info.port)
                discovered[info.serviceName] = host
                mainHandler.post { onDiscoveryUpdated?.invoke(discovered.values.toList()) }
                synchronized(resolveQueue) { resolveNext() }
            }
        })
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    fun connect(host: String, port: Int = HOST_PORT) {
        running.set(true)
        Thread({
            try {
                val addr = InetAddress.getByName(host)
                val s = Socket()
                s.connect(java.net.InetSocketAddress(addr, port), 5000)
                s.tcpNoDelay = true
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                mainHandler.post { listener?.onConnected() }
                readLoop(s)
            } catch (e: IOException) {
                mainHandler.post { listener?.onConnectFailed(e.message ?: "Connection failed") }
            }
        }, "GameClient-Connect").start()
    }

    private fun readLoop(s: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            while (running.get()) {
                val line = reader.readLine() ?: break
                val obj = try { JSONObject(line) } catch (_: Exception) { continue }
                mainHandler.post { listener?.onMessage(obj) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        } finally {
            mainHandler.post { listener?.onDisconnected() }
        }
    }

    override fun send(json: String) {
        val w = writer ?: return
        try {
            w.println(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send input: ${e.message}")
        }
    }

    override fun disconnect() {
        running.set(false)
        stopDiscovery()
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    companion object {
        private const val TAG = "GameClient"
    }
}
