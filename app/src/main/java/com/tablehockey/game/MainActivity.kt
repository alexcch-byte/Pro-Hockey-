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
