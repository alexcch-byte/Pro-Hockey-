package com.tablehockey.game

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnPlayNow).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, MatchSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnTournament).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, TournamentActivity::class.java))
        }
        findViewById<Button>(R.id.btnShootout).setOnClickListener {
            MusicManager.click(this)
            val config = com.tablehockey.game.model.MatchConfig(
                mode = com.tablehockey.game.model.GameMode.SHOOTOUT,
                homeTeam = com.tablehockey.game.model.TeamInfo.DEFAULT_HOME,
                awayTeam = com.tablehockey.game.model.TeamInfo.DEFAULT_AWAY,
                periodLengthSeconds = 120,
                aiDifficulty = com.tablehockey.game.model.AiDifficulty.MEDIUM,
                soundEnabled = true
            )
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(com.tablehockey.game.model.MatchConfig.EXTRA_KEY, config)
            }
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnWifi2p).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, WifiLobbyActivity::class.java))
        }
        findViewById<Button>(R.id.btnHowToPlay).setOnClickListener {
            MusicManager.click(this)
            startActivity(Intent(this, HowToPlayActivity::class.java))
        }
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            MusicManager.stop()
            finishAffinity()
        }
    }

    override fun onStart() {
        super.onStart()
        MusicManager.menuStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.menuStopped()
    }
}
