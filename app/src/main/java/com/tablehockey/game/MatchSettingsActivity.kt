package com.tablehockey.game

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.AiDifficulty
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.Prefs
import com.tablehockey.game.model.TeamInfo

class MatchSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_settings)

        val spinnerHome = findViewById<Spinner>(R.id.spinnerHomeTeam)
        val spinnerAway = findViewById<Spinner>(R.id.spinnerAwayTeam)
        val names = TeamInfo.ALL.map { TeamInfo.label(it) }
        val adapter = ArrayAdapter(this, R.layout.item_spinner, names).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        spinnerHome.adapter = adapter
        spinnerAway.adapter = adapter
        spinnerHome.setSelection(TeamInfo.DEFAULT_HOME)
        spinnerAway.setSelection(TeamInfo.DEFAULT_AWAY)

        val radioPeriod = findViewById<RadioGroup>(R.id.radioPeriodLength)
        val radioDifficulty = findViewById<RadioGroup>(R.id.radioDifficulty)
        AudioSliders.bind(findViewById(R.id.audioSliders), this)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            MusicManager.click(this)
            var home = spinnerHome.selectedItemPosition
            var away = spinnerAway.selectedItemPosition
            if (home == away) away = (away + 1) % TeamInfo.ALL.size
            val periodLength = when (radioPeriod.checkedRadioButtonId) {
                R.id.period1 -> 60
                R.id.period3 -> 180
                R.id.period5 -> 300
                else -> 120
            }
            val difficulty = when (radioDifficulty.checkedRadioButtonId) {
                R.id.diffEasy -> AiDifficulty.EASY
                R.id.diffHard -> AiDifficulty.HARD
                else -> AiDifficulty.MEDIUM
            }
            val config = MatchConfig(
                mode = GameMode.SINGLE_PLAYER,
                homeTeam = home,
                awayTeam = away,
                periodLengthSeconds = periodLength,
                aiDifficulty = difficulty,
                soundEnabled = Prefs.soundEnabled(this),
                musicEnabled = Prefs.musicEnabled(this)
            )
            startActivity(Intent(this, GameActivity::class.java).putExtra(MatchConfig.EXTRA_KEY, config))
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
