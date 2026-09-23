package com.tablehockey.game

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tablehockey.game.game.GameView
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.game.SoundManager
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.Prefs
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.network.NetworkSession

class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var config: MatchConfig
    private lateinit var soundManager: SoundManager

    private var dialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @Suppress("DEPRECATION")
        config = intent.getSerializableExtra(MatchConfig.EXTRA_KEY) as? MatchConfig ?: MatchConfig.default()

        // The menu theme stops here; the game view starts its own loop at the saved level.
        MusicManager.stop()
        MusicManager.setUserVolume(this, Prefs.musicVolume(this) / 100f)

        gameView = findViewById(R.id.gameView)
        soundManager = SoundManager(this).apply { volume = Prefs.sfxVolume(this@GameActivity) / 100f }
        gameView.soundManager = soundManager

        val server = if (config.mode == GameMode.WIFI_HOST) NetworkSession.host else null
        val client = if (config.mode == GameMode.WIFI_CLIENT) NetworkSession.guest else null
        gameView.configure(config, server, client)

        gameView.listener = object : GameView.GameListener {
            override fun onPauseRequested() { showPauseDialog() }
            override fun onMatchOver(homeScore: Int, awayScore: Int, localWon: Boolean?) {
                showMatchOverDialog(homeScore, awayScore, localWon)
            }
            override fun onOpponentConnectionLost() { showDisconnectedDialog() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dialogShowing) showPauseDialog()
            }
        })
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showPauseDialog() {
        if (dialogShowing) return
        dialogShowing = true
        gameView.pause()
        soundManager.playClick()

        // Audio sliders live inside the pause menu so music can be turned down or off mid-game.
        val sliders = LayoutInflater.from(this).inflate(R.layout.dialog_audio, null)
        AudioSliders.bind(sliders, this) { level -> soundManager.volume = level }

        val btnPull = sliders.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPullGoalie)
        val initialPulled = gameView.isGoaliePulled()
        btnPull?.text = if (initialPulled) "RETURN GOALIE TO CREASE" else "PULL GOALIE (EXTRA ATTACKER)"
        btnPull?.setOnClickListener {
            val nowPulled = gameView.togglePullGoalie()
            btnPull.text = if (nowPulled) "RETURN GOALIE TO CREASE" else "PULL GOALIE (EXTRA ATTACKER)"
            soundManager.playClick()
        }

        val btnArena = sliders.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleArena)
        fun updateArenaBtnText(type: com.tablehockey.game.model.ArenaType) {
            btnArena?.text = if (type == com.tablehockey.game.model.ArenaType.WINTER_POND) "ARENA: WINTER POND (OUTDOOR)" else "ARENA: INDOOR STADIUM"
        }
        updateArenaBtnText(gameView.getArenaType())
        btnArena?.setOnClickListener {
            val nextType = gameView.toggleArena()
            updateArenaBtnText(nextType)
            com.tablehockey.game.model.Prefs.setArenaType(this, nextType)
            soundManager.playClick()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_paused))
            .setView(sliders)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.game_resume)) { d, _ ->
                d.dismiss()
                dialogShowing = false
                enterImmersive()
                gameView.resume()
            }
            .setNegativeButton(getString(R.string.game_quit)) { d, _ ->
                d.dismiss()
                quitToMenu()
            }
        if (config.mode == GameMode.SINGLE_PLAYER || config.mode == GameMode.WIFI_HOST) {
            builder.setNeutralButton(getString(R.string.game_restart)) { d, _ ->
                d.dismiss()
                dialogShowing = false
                enterImmersive()
                gameView.restartMatch()
                gameView.resume()
            }
        }
        builder.show()
    }

    private fun showMatchOverDialog(homeScore: Int, awayScore: Int, localWon: Boolean?) {
        if (dialogShowing) return
        dialogShowing = true
        val home = TeamInfo.byIndex(config.homeTeam)
        val away = TeamInfo.byIndex(config.awayTeam)
        val title = when (localWon) {
            true -> getString(R.string.you_win)
            false -> getString(R.string.you_lose)
            null -> getString(R.string.game_over)
        }
        val isTournament = intent.getBooleanExtra("IS_TOURNAMENT", false)
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("${home.fullName} $homeScore\n${away.fullName} $awayScore")
            .setCancelable(false)

        if (isTournament) {
            builder.setPositiveButton("CONTINUE TO BRACKET") { d, _ ->
                d.dismiss()
                val resultIntent = Intent().apply {
                    putExtra("HOME_SCORE", homeScore)
                    putExtra("AWAY_SCORE", awayScore)
                    putExtra("LOCAL_WON", localWon == true)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            builder.setNegativeButton("EXIT TOURNAMENT") { d, _ ->
                d.dismiss()
                quitToMenu()
            }
        } else {
            builder.setNegativeButton(getString(R.string.game_quit)) { d, _ ->
                d.dismiss()
                quitToMenu()
            }
            if (config.mode == GameMode.SINGLE_PLAYER || config.mode == GameMode.WIFI_HOST) {
                builder.setPositiveButton(getString(R.string.rematch)) { d, _ ->
                    d.dismiss()
                    dialogShowing = false
                    enterImmersive()
                    gameView.restartMatch()
                    gameView.resume()
                }
            }
        }
        builder.show()
    }

    private fun showDisconnectedDialog() {
        if (dialogShowing) return
        dialogShowing = true
        gameView.pause()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.opponent_disconnected))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.game_quit)) { d, _ ->
                d.dismiss()
                quitToMenu()
            }
            .show()
    }

    private fun quitToMenu() {
        NetworkSession.clear()
        MusicManager.stop()
        if (intent.getBooleanExtra("IS_TOURNAMENT", false)) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        if (!dialogShowing) gameView.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.release()
        soundManager.release()
        if (isFinishing) {
            MusicManager.stop()
            if (config.mode == GameMode.WIFI_HOST || config.mode == GameMode.WIFI_CLIENT) {
                NetworkSession.clear()
            }
        }
    }
}
