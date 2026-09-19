package com.tablehockey.game

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.AiDifficulty
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.network.BluetoothGuest
import com.tablehockey.game.network.BluetoothHost
import com.tablehockey.game.network.BluetoothScanner
import com.tablehockey.game.network.BluetoothSupport
import com.tablehockey.game.network.DiscoveredHost
import com.tablehockey.game.network.GameClient
import com.tablehockey.game.network.GameServer
import com.tablehockey.game.network.GuestLink
import com.tablehockey.game.network.HOST_PORT
import com.tablehockey.game.network.HostLink
import com.tablehockey.game.network.NetworkSession
import org.json.JSONObject

/**
 * Two-tablet multiplayer lobby. The host picks the clubs and period length;
 * the guest finds the host over WiFi (NSD or a typed IP) or over Bluetooth
 * (paired devices, or a scan). Both transports hand a link to GameActivity.
 */
class WifiLobbyActivity : AppCompatActivity() {

    private enum class Connection { WIFI, BLUETOOTH }
    private enum class Pending { NONE, HOST, JOIN, SCAN }

    private lateinit var chooserLayout: LinearLayout
    private lateinit var hostLayout: LinearLayout
    private lateinit var joinLayout: LinearLayout
    private lateinit var hostStatusText: TextView
    private lateinit var joinStatusText: TextView
    private lateinit var hostHintText: TextView
    private lateinit var joinHintText: TextView
    private lateinit var discoveredListContainer: LinearLayout
    private lateinit var radioHostPeriod: RadioGroup
    private lateinit var radioConnection: RadioGroup
    private lateinit var spinnerHostTeam: Spinner
    private lateinit var spinnerGuestTeam: Spinner
    private lateinit var editHostIp: EditText
    private lateinit var manualIpRow: LinearLayout
    private lateinit var btnDiscoverable: Button
    private lateinit var btnScan: Button
    private lateinit var btnStartHosting: Button

    private var wifiServer: GameServer? = null
    private var wifiClient: GameClient? = null
    private var btHost: BluetoothHost? = null
    private var btGuest: BluetoothGuest? = null
    private var btScanner: BluetoothScanner? = null
    private val btFound = LinkedHashMap<String, BluetoothDevice>()
    private var handedOff = false
    private var pending = Pending.NONE

    private val connection: Connection
        get() = if (radioConnection.checkedRadioButtonId == R.id.connBluetooth) Connection.BLUETOOTH else Connection.WIFI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_lobby)

        chooserLayout = findViewById(R.id.chooserLayout)
        hostLayout = findViewById(R.id.hostLayout)
        joinLayout = findViewById(R.id.joinLayout)
        hostStatusText = findViewById(R.id.hostStatusText)
        joinStatusText = findViewById(R.id.joinStatusText)
        hostHintText = findViewById(R.id.hostHintText)
        joinHintText = findViewById(R.id.joinHintText)
        discoveredListContainer = findViewById(R.id.discoveredListContainer)
        radioHostPeriod = findViewById(R.id.radioHostPeriod)
        radioConnection = findViewById(R.id.radioConnection)
        spinnerHostTeam = findViewById(R.id.spinnerHostTeam)
        spinnerGuestTeam = findViewById(R.id.spinnerGuestTeam)
        editHostIp = findViewById(R.id.editHostIp)
        manualIpRow = findViewById(R.id.manualIpRow)
        btnDiscoverable = findViewById(R.id.btnDiscoverable)
        btnScan = findViewById(R.id.btnScan)
        btnStartHosting = findViewById(R.id.btnStartHosting)

        val adapter = ArrayAdapter(this, R.layout.item_spinner, TeamInfo.ALL.map { TeamInfo.label(it) }).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerHostTeam.adapter = adapter
        spinnerGuestTeam.adapter = adapter
        spinnerHostTeam.setSelection(TeamInfo.DEFAULT_HOME)
        spinnerGuestTeam.setSelection(TeamInfo.DEFAULT_AWAY)

        if (!BluetoothSupport.isAvailable(this)) {
            findViewById<View>(R.id.connBluetooth).isEnabled = false
        }
        radioConnection.setOnCheckedChangeListener { _, _ -> applyConnectionUi() }

        findViewById<Button>(R.id.btnHostGame).setOnClickListener {
            MusicManager.click(this)
            chooserLayout.visibility = View.GONE
            hostLayout.visibility = View.VISIBLE
            applyConnectionUi()
        }
        findViewById<Button>(R.id.btnJoinGame).setOnClickListener {
            MusicManager.click(this)
            chooserLayout.visibility = View.GONE
            joinLayout.visibility = View.VISIBLE
            applyConnectionUi()
            startJoinFlow()
        }
        btnStartHosting.setOnClickListener { MusicManager.click(this); startHostingFlow() }
        btnDiscoverable.setOnClickListener { MusicManager.click(this); requestDiscoverable() }
        btnScan.setOnClickListener { MusicManager.click(this); startBluetoothScan() }
        findViewById<Button>(R.id.btnCancelHost).setOnClickListener {
            MusicManager.click(this)
            stopEverything()
            hostStatusText.visibility = View.GONE
            btnStartHosting.isEnabled = true
            hostLayout.visibility = View.GONE
            chooserLayout.visibility = View.VISIBLE
        }
        findViewById<Button>(R.id.btnCancelJoin).setOnClickListener {
            MusicManager.click(this)
            stopEverything()
            joinLayout.visibility = View.GONE
            chooserLayout.visibility = View.VISIBLE
        }
        findViewById<Button>(R.id.btnConnectIp).setOnClickListener {
            val ip = editHostIp.text.toString().trim()
            if (ip.isNotEmpty()) connectWifi(ip, HOST_PORT)
        }
        applyConnectionUi()
    }

    private fun applyConnectionUi() {
        val bt = connection == Connection.BLUETOOTH
        btnDiscoverable.visibility = if (bt) View.VISIBLE else View.GONE
        hostHintText.visibility = if (bt) View.VISIBLE else View.GONE
        btnScan.visibility = if (bt) View.VISIBLE else View.GONE
        manualIpRow.visibility = if (bt) View.GONE else View.VISIBLE
        joinHintText.text = getString(if (bt) R.string.bt_hint_join else R.string.wifi_hint_join)
    }

    private fun matchConfig(mode: GameMode): MatchConfig {
        val periodLength = when (radioHostPeriod.checkedRadioButtonId) {
            R.id.hostPeriod1 -> 60
            R.id.hostPeriod3 -> 180
            R.id.hostPeriod5 -> 300
            else -> 120
        }
        var home = spinnerHostTeam.selectedItemPosition
        var away = spinnerGuestTeam.selectedItemPosition
        if (home == away) away = (away + 1) % TeamInfo.ALL.size
        return MatchConfig(
            mode = mode,
            homeTeam = home,
            awayTeam = away,
            periodLengthSeconds = periodLength,
            aiDifficulty = AiDifficulty.MEDIUM,
            soundEnabled = true
        )
    }

    // ------------------------------------------------------------------ host

    private fun startHostingFlow() {
        if (connection == Connection.BLUETOOTH) startBluetoothHost() else startWifiHost()
    }

    private fun startWifiHost() {
        val config = matchConfig(GameMode.WIFI_HOST)
        val s = GameServer(this)
        wifiServer = s
        s.listener = hostListener(s, config)
        s.start()
        hostStatusText.visibility = View.VISIBLE
        hostStatusText.text = getString(R.string.wifi_hosting_status)
        btnStartHosting.isEnabled = false
    }

    private fun startBluetoothHost() {
        if (!ensureBluetoothReady(Pending.HOST, BluetoothSupport.connectPermissions())) return
        val config = matchConfig(GameMode.WIFI_HOST)
        val h = BluetoothHost(this)
        btHost = h
        h.listener = hostListener(h, config)
        if (!h.start()) {
            Toast.makeText(this, getString(R.string.bt_host_failed), Toast.LENGTH_LONG).show()
            btHost = null
            return
        }
        hostStatusText.visibility = View.VISIBLE
        hostStatusText.text = getString(R.string.bt_hosting_status, BluetoothSupport.localName(this))
        btnStartHosting.isEnabled = false
    }

    private fun hostListener(link: HostLink, config: MatchConfig) = object : HostLink.Listener {
        override fun onClientConnected() {
            NetworkSession.host = link
            handedOff = true
            startActivity(Intent(this@WifiLobbyActivity, GameActivity::class.java).putExtra(MatchConfig.EXTRA_KEY, config))
            finish()
        }
        override fun onClientDisconnected() {}
        override fun onMessage(obj: JSONObject) {}
    }

    private fun requestDiscoverable() {
        if (!ensureBluetoothReady(Pending.NONE, BluetoothSupport.connectPermissions())) return
        try {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.bt_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ join

    private fun startJoinFlow() {
        discoveredListContainer.removeAllViews()
        if (connection == Connection.BLUETOOTH) startBluetoothJoin() else startWifiJoin()
    }

    private fun startWifiJoin() {
        joinStatusText.text = getString(R.string.wifi_searching_status)
        val c = GameClient(this)
        wifiClient = c
        c.onDiscoveryUpdated = { hosts -> showWifiHosts(hosts) }
        c.listener = guestListener(c)
        c.startDiscovery()
    }

    private fun showWifiHosts(hosts: List<DiscoveredHost>) {
        discoveredListContainer.removeAllViews()
        if (hosts.isEmpty()) {
            joinStatusText.text = getString(R.string.wifi_no_games_found)
        } else {
            joinStatusText.text = getString(R.string.wifi_searching_status)
            for (h in hosts) {
                addDeviceButton(h.name) { connectWifi(h.address.hostAddress ?: return@addDeviceButton, h.port) }
            }
        }
    }

    private fun connectWifi(ip: String, port: Int) {
        joinStatusText.text = getString(R.string.wifi_connecting_status)
        wifiClient?.stopDiscovery()
        wifiClient?.connect(ip, port)
    }

    private fun startBluetoothJoin() {
        if (!ensureBluetoothReady(Pending.JOIN, BluetoothSupport.connectPermissions())) return
        btFound.clear()
        joinStatusText.text = getString(R.string.bt_paired)
        refreshBluetoothList()
    }

    private fun refreshBluetoothList() {
        discoveredListContainer.removeAllViews()
        val paired = BluetoothSupport.bondedDevices(this)
        for (d in paired) addDeviceButton(BluetoothSupport.deviceName(d)) { connectBluetooth(d) }
        for (d in btFound.values) {
            if (paired.any { it.address == d.address }) continue
            addDeviceButton(BluetoothSupport.deviceName(d) + "  (nearby)") { connectBluetooth(d) }
        }
        if (discoveredListContainer.childCount == 0) {
            joinStatusText.text = getString(R.string.bt_none_found)
        }
    }

    private fun startBluetoothScan() {
        if (!ensureBluetoothReady(Pending.SCAN, BluetoothSupport.scanPermissions())) return
        btScanner?.stop()
        val scanner = BluetoothScanner(this,
            onFound = { d ->
                btFound[d.address] = d
                refreshBluetoothList()
                joinStatusText.text = getString(R.string.bt_scanning)
            },
            onFinished = {
                joinStatusText.text = getString(if (discoveredListContainer.childCount == 0) R.string.bt_none_found else R.string.bt_paired)
            })
        btScanner = scanner
        if (scanner.start()) {
            joinStatusText.text = getString(R.string.bt_scanning)
        } else {
            Toast.makeText(this, getString(R.string.bt_scan_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectBluetooth(device: BluetoothDevice) {
        joinStatusText.text = getString(R.string.wifi_connecting_status)
        btScanner?.stop()
        btGuest?.disconnect()
        val g = BluetoothGuest(this)
        btGuest = g
        g.listener = guestListener(g)
        g.connect(device)
    }

    private fun guestListener(link: GuestLink) = object : GuestLink.Listener {
        override fun onConnected() {
            val config = MatchConfig(
                mode = GameMode.WIFI_CLIENT,
                homeTeam = 0,
                awayTeam = 1,
                periodLengthSeconds = 120,
                aiDifficulty = AiDifficulty.MEDIUM,
                soundEnabled = true
            )
            NetworkSession.guest = link
            handedOff = true
            startActivity(Intent(this@WifiLobbyActivity, GameActivity::class.java).putExtra(MatchConfig.EXTRA_KEY, config))
            finish()
        }
        override fun onConnectFailed(reason: String) {
            Toast.makeText(this@WifiLobbyActivity, getString(R.string.connect_failed, reason), Toast.LENGTH_SHORT).show()
            joinStatusText.text = getString(if (connection == Connection.BLUETOOTH) R.string.bt_paired else R.string.wifi_searching_status)
        }
        override fun onDisconnected() {}
        override fun onMessage(obj: JSONObject) {}
    }

    private fun addDeviceButton(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { MusicManager.click(this@WifiLobbyActivity); onClick() }
        }
        discoveredListContainer.addView(btn)
    }

    // ----------------------------------------------------------- bluetooth

    /** Checks adapter + permissions; remembers [action] to resume after a permission grant. */
    private fun ensureBluetoothReady(action: Pending, perms: Array<String>): Boolean {
        if (!BluetoothSupport.isAvailable(this)) {
            Toast.makeText(this, getString(R.string.bt_unavailable), Toast.LENGTH_LONG).show()
            return false
        }
        if (perms.isNotEmpty() && !BluetoothSupport.hasPermissions(this, perms)) {
            pending = action
            ActivityCompat.requestPermissions(this, perms, REQ_BT_PERMS)
            return false
        }
        if (!BluetoothSupport.isEnabled(this)) {
            pending = action
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_BT_ENABLE)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.bt_enable), Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    private fun resumePending() {
        val p = pending
        pending = Pending.NONE
        when (p) {
            Pending.HOST -> startBluetoothHost()
            Pending.JOIN -> startBluetoothJoin()
            Pending.SCAN -> startBluetoothScan()
            Pending.NONE -> {}
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_BT_PERMS) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            resumePending()
        } else {
            pending = Pending.NONE
            Toast.makeText(this, getString(R.string.bt_permission), Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BT_ENABLE) {
            if (BluetoothSupport.isEnabled(this)) resumePending() else pending = Pending.NONE
        }
    }

    // ------------------------------------------------------------- lifecycle

    private fun stopEverything() {
        wifiServer?.stop(); wifiServer = null
        wifiClient?.disconnect(); wifiClient = null
        btHost?.stop(); btHost = null
        btGuest?.disconnect(); btGuest = null
        btScanner?.stop(); btScanner = null
    }

    override fun onStart() {
        super.onStart()
        MusicManager.menuStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.menuStopped()
    }

    override fun onDestroy() {
        super.onDestroy()
        btScanner?.stop()
        if (!handedOff) stopEverything()
    }

    companion object {
        private const val REQ_BT_PERMS = 41
        private const val REQ_BT_ENABLE = 42
    }
}
