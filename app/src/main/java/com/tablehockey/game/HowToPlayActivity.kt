package com.tablehockey.game

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager

/** Full-screen, large-type controls guide with a diagram of the touch layout. */
class HowToPlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_how_to_play)
        findViewById<Button>(R.id.btnDone).setOnClickListener {
            MusicManager.click(this)
            finish()
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
