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
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosts a LAN match: advertises via NSD, accepts one client connection,
 * receives the client's controller input and pushes authoritative state out.
 */
class GameServer(private val context: Context) : HostLink {

    override var listener: HostLink.Listener? = null
    override val stateHz: Int = 30

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    private val nsdManager: NsdManager by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile var deviceName: String = "Hockey on " + android.os.Build.MODEL

    fun start() {
        if (running.getAndSet(true)) return
        Thread({ acceptLoop() }, "GameServer-Accept").start()
    }

    private fun acceptLoop() {
        try {
            val socket = ServerSocket(HOST_PORT)
            serverSocket = socket
            registerNsd(socket.localPort)

            while (running.get()) {
                val incoming = socket.accept()
                incoming.tcpNoDelay = true
                clientSocket = incoming
                writer = PrintWriter(incoming.getOutputStream(), true)
                mainHandler.post { listener?.onClientConnected() }
                readLoop(incoming)
                mainHandler.post { listener?.onClientDisconnected() }
                writer = null
                clientSocket = null
                if (!running.get()) break
            }
        } catch (e: IOException) {
            Log.w(TAG, "Accept loop ended: ${e.message}")
        }
    }

    private fun readLoop(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (running.get()) {
                val line = reader.readLine() ?: break
                val obj = try { JSONObject(line) } catch (_: Exception) { continue }
                mainHandler.post { listener?.onMessage(obj) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Client read loop ended: ${e.message}")
        }
    }

    /** Call from any thread; safe no-op if no client connected yet. */
    override fun send(json: String) {
        val w = writer ?: return
        try {
            w.println(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send state: ${e.message}")
        }
    }

    private fun registerNsd(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD registration error: ${e.message}")
        }
    }

    override fun stop() {
        running.set(false)
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null
        try { writer?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        writer = null
        clientSocket = null
        serverSocket = null
    }

    companion object {
        private const val TAG = "GameServer"
    }
}
