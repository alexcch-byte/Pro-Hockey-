package com.tablehockey.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.model.TeamStyleStore
import com.tablehockey.game.ui.MenuBackdropView

class MainActivity : AppCompatActivity() {

    private lateinit var backdrop: MenuBackdropView
    private lateinit var txtClub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TeamStyleStore.ensureLoaded(this)
        setContentView(R.layout.activity_main)
        backdrop = findViewById(R.id.backdrop)
        txtClub = findViewById(R.id.txtClub)

        findViewById<View>(R.id.btnPlayNow).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, MatchSettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnTournament).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, TournamentActivity::class.java))
        }
        findViewById<View>(R.id.btnShootout).setOnClickListener {
            MusicManager.click(this)
            val fav = TeamStyleStore.favourite(this)
            val config = MatchConfig(
                mode = GameMode.SHOOTOUT,
                homeTeam = fav,
                awayTeam = if (fav == TeamInfo.DEFAULT_AWAY) TeamInfo.DEFAULT_HOME else TeamInfo.DEFAULT_AWAY,
                periodLengthSeconds = 120,
                aiDifficulty = com.tablehockey.game.model.AiDifficulty.MEDIUM,
                soundEnabled = true
            )
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(MatchConfig.EXTRA_KEY, config)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnWifi2p).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, WifiLobbyActivity::class.java))
        }
        findViewById<View>(R.id.btnCustomise).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, CustomiseTeamActivity::class.java))
        }
        findViewById<View>(R.id.btnHowToPlay).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, HowToPlayActivity::class.java))
        }
        findViewById<View>(R.id.btnExit).setOnClickListener {
            MusicManager.stop()
            finishAffinity()
        }
    }

    override fun onStart() {
        super.onStart()
        MusicManager.menuStarted(this)
        // Hero art follows the favourite club and any uniform edits made on the Customise screen.
        val fav = TeamStyleStore.favourite(this)
        backdrop.setTeam(fav)
        txtClub.text = getString(R.string.menu_your_club, TeamInfo.ALL[fav].fullName)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.menuStopped()
    }
}
