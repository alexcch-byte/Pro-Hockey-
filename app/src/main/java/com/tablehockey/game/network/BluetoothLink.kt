package com.tablehockey.game.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** RFCOMM service the host advertises; guests look it up by UUID. */
val HOCKEY_BT_UUID: UUID = UUID.fromString("7a4b1c2e-9d3f-4b6a-8e21-5c0f2d9a7b31")
const val BT_SERVICE_NAME = "PowerPlayHockey"

/** Helpers for adapter state and the per-OS-version permission sets. */
object BluetoothSupport {
    private const val TAG = "BluetoothSupport"

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isAvailable(context: Context) = adapter(context) != null

    fun isEnabled(context: Context) = try { adapter(context)?.isEnabled == true } catch (_: SecurityException) { false }

    /** Permissions needed to host or connect. */
    fun connectPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else emptyArray()

    /** Permissions needed to discover unpaired devices. */
    fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context, perms: Array<String>): Boolean =
        perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun bondedDevices(context: Context): List<BluetoothDevice> = try {
        adapter(context)?.bondedDevices?.toList() ?: emptyList()
    } catch (e: SecurityException) {
        Log.w(TAG, "No permission to list bonded devices")
        emptyList()
    }

    fun deviceName(device: BluetoothDevice): String = try {
        device.name ?: device.address
    } catch (_: SecurityException) {
        device.address
    }

    fun localName(context: Context): String = try {
        adapter(context)?.name ?: Build.MODEL
    } catch (_: SecurityException) {
        Build.MODEL
    }
}

/**
 * Hosts a Bluetooth match: listens on an RFCOMM channel and accepts one
 * guest. Same protocol as the WiFi server, at a gentler snapshot rate.
 *
 * Listens on BOTH a secure and an insecure server socket at once, since a
 * secure RFCOMM socket demands the two devices be bonded first - if they
 * aren't, Android silently tries to auto-pair them and that attempt fails in
 * well under a second, long before a person could tap the pairing prompt,
 * which kills the connection. Running both means whichever kind of socket
 * the guest manages to reach has a matching listener on this end; only the
 * first guest to actually connect is accepted; a second one is turned away.
 */
class BluetoothHost(private val context: Context) : HostLink {

    override var listener: HostLink.Listener? = null
    override val stateHz: Int = 20

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val guestClaimed = AtomicBoolean(false)
    private var secureServerSocket: BluetoothServerSocket? = null
    private var insecureServerSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    @Volatile private var writer: PrintWriter? = null

    /** Returns false when Bluetooth is off, missing, or not permitted. */
    fun start(): Boolean {
        if (running.get()) return true
        val adapter = BluetoothSupport.adapter(context) ?: return false
        val secure = try {
            adapter.listenUsingRfcommWithServiceRecord(BT_SERVICE_NAME, HOCKEY_BT_UUID)
        } catch (e: Exception) {
            Log.w(TAG, "secure listen failed: ${e.message}")
            null
        }
        val insecure = try {
            adapter.listenUsingInsecureRfcommWithServiceRecord(BT_SERVICE_NAME, HOCKEY_BT_UUID)
        } catch (e: Exception) {
            Log.w(TAG, "insecure listen failed: ${e.message}")
            null
        }
        if (secure == null && insecure == null) return false
        secureServerSocket = secure
        insecureServerSocket = insecure
        running.set(true)
        secure?.let { Thread({ acceptLoop(it, "secure") }, "BluetoothHost-Accept-Secure").start() }
        insecure?.let { Thread({ acceptLoop(it, "insecure") }, "BluetoothHost-Accept-Insecure").start() }
        return true
    }

    private fun acceptLoop(server: BluetoothServerSocket, label: String) {
        while (running.get()) {
            val incoming = try { server.accept() } catch (e: IOException) { break }
            if (!guestClaimed.compareAndSet(false, true)) {
                // Already serving a guest through the other socket type; turn this one away.
                try { incoming.close() } catch (_: Exception) {}
                continue
            }
            Log.i(TAG, "Guest connected via $label socket")
            socket = incoming
            writer = try { PrintWriter(incoming.outputStream, true) } catch (e: IOException) { null }
            mainHandler.post { listener?.onClientConnected() }
            readLoop(incoming)
            mainHandler.post { listener?.onClientDisconnected() }
            writer = null
            try { incoming.close() } catch (_: Exception) {}
            socket = null
            guestClaimed.set(false)
        }
    }

    private fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            while (running.get()) {
                val line = reader.readLine() ?: break
                val obj = try { JSONObject(line) } catch (_: Exception) { continue }
                mainHandler.post { listener?.onMessage(obj) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Guest read loop ended: ${e.message}")
        }
    }

    override fun send(json: String) {
        val w = writer ?: return
        try { w.println(json) } catch (e: Exception) { Log.w(TAG, "send failed: ${e.message}") }
    }

    override fun stop() {
        running.set(false)
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { secureServerSocket?.close() } catch (_: Exception) {}
        try { insecureServerSocket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
        secureServerSocket = null
        insecureServerSocket = null
        guestClaimed.set(false)
    }

    companion object {
        private const val TAG = "BluetoothHost"
    }
}

/** Guest side of a Bluetooth match: connects to a host device by service UUID. */
class BluetoothGuest(private val context: Context) : GuestLink {

    override var listener: GuestLink.Listener? = null
    override val inputHz: Int = 20

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var socket: BluetoothSocket? = null
    @Volatile private var writer: PrintWriter? = null

    fun connect(device: BluetoothDevice) {
        running.set(true)
        Thread({ runConnect(device) }, "BluetoothGuest-Connect").start()
    }

    /**
     * Tries, in order: the insecure and secure UUID/SDP-based sockets (the public,
     * documented API), then two hidden-API fallbacks that connect straight to RFCOMM
     * channel 1 - the conventional first SPP channel - bypassing SDP entirely.
     *
     * Insecure goes first on purpose. A *secure* RFCOMM socket demands the two
     * devices be bonded, and on an unbonded pair Android tries to silently
     * auto-pair as part of `connect()`; with nobody able to tap a consent prompt
     * that fast, the bond flips to BONDING then back to NONE in well under a
     * second and the connection fails - the error surfaces as "read failed,
     * socket might closed or timeout", which looks like a plain I/O glitch but
     * is really that failed auto-pair. Insecure sockets skip bonding entirely,
     * which is what a same-room game between two unpaired tablets actually wants.
     * The channel-1 fallbacks bypass the SDP UUID lookup too, for stacks where
     * that lookup itself is unreliable; since this app registers exactly one
     * RFCOMM service, the host is almost always allocated channel 1.
     */
    private fun runConnect(device: BluetoothDevice) {
        try { BluetoothSupport.adapter(context)?.cancelDiscovery() } catch (_: SecurityException) {}
        // Give discovery time to fully stop; connecting while it's still winding down
        // is itself a common cause of the same "read failed" error.
        try { Thread.sleep(200) } catch (_: InterruptedException) {}

        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "insecure/UUID" to { device.createInsecureRfcommSocketToServiceRecord(HOCKEY_BT_UUID) },
            "secure/UUID" to { device.createRfcommSocketToServiceRecord(HOCKEY_BT_UUID) },
            "insecure/channel1" to { reflectRfcommSocket(device, secure = false) },
            "secure/channel1" to { reflectRfcommSocket(device, secure = true) }
        )

        var connected: BluetoothSocket? = null
        var lastError: Exception? = null
        for ((index, attempt) in attempts.withIndex()) {
            if (!running.get()) return
            val (label, factory) = attempt
            val s = try { factory() } catch (e: Exception) { lastError = e; null } ?: continue
            try {
                s.connect()
                connected = s
                break
            } catch (e: IOException) {
                lastError = e
                try { s.close() } catch (_: Exception) {}
                Log.w(TAG, "Connect attempt via $label failed: ${e.message}")
                if (index < attempts.lastIndex) try { Thread.sleep(250) } catch (_: InterruptedException) {}
            }
        }

        val s = connected
        if (s == null) {
            if (running.get()) {
                val reason = lastError?.message ?: "Bluetooth connection failed"
                mainHandler.post { listener?.onConnectFailed(reason) }
            }
            return
        }
        socket = s
        writer = PrintWriter(s.outputStream, true)
        mainHandler.post { listener?.onConnected() }
        readLoop(s)
    }

    private fun reflectRfcommSocket(device: BluetoothDevice, secure: Boolean): BluetoothSocket {
        val methodName = if (secure) "createRfcommSocket" else "createInsecureRfcommSocket"
        val method = device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    private fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
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
        try { w.println(json) } catch (e: Exception) { Log.w(TAG, "send failed: ${e.message}") }
    }

    override fun disconnect() {
        running.set(false)
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    companion object {
        private const val TAG = "BluetoothGuest"
    }
}

/** Classic discovery of nearby devices; the caller must hold the scan permissions. */
class BluetoothScanner(
    private val context: Context,
    private val onFound: (BluetoothDevice) -> Unit,
    private val onFinished: () -> Unit
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) onFound(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
            }
        }
    }

    fun start(): Boolean {
        val adapter = BluetoothSupport.adapter(context) ?: return false
        if (!registered) {
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            })
            registered = true
        }
        return try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop() {
        try { BluetoothSupport.adapter(context)?.cancelDiscovery() } catch (_: SecurityException) {}
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
    }
}
