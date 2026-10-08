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

    private var teamIdx = IntArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_settings)

        val spinnerHome = findViewById<Spinner>(R.id.spinnerHomeTeam)
        val spinnerAway = findViewById<Spinner>(R.id.spinnerAwayTeam)
        com.tablehockey.game.model.TeamStyleStore.ensureLoaded(this)
        val favourite = com.tablehockey.game.model.TeamStyleStore.favourite(this)
        val radioLeague = findViewById<RadioGroup>(R.id.radioLeague)

        // Repopulates both pickers from one league; spinner positions index into `teamIdx` (TeamInfo.ALL indices).
        fun populate(league: String) {
            teamIdx = TeamInfo.indicesFor(league)
            val names = teamIdx.map { TeamInfo.ALL[it].fullName }
            val adapter = ArrayAdapter(this, R.layout.item_spinner, names).apply {
                setDropDownViewResource(R.layout.item_spinner_dropdown)
            }
            spinnerHome.adapter = adapter
            spinnerAway.adapter = adapter
            val favPos = teamIdx.indexOf(favourite)
            val homePos = if (favPos >= 0) favPos else 0
            spinnerHome.setSelection(homePos)
            spinnerAway.setSelection(if (homePos == 1) 0 else 1)
        }
        val league0 = Prefs.league(this)
        radioLeague.check(if (league0 == TeamInfo.LEAGUE_NHL) R.id.leagueNhl else R.id.leagueTimbits)
        populate(league0)
        radioLeague.setOnCheckedChangeListener { _, id ->
            val l = if (id == R.id.leagueNhl) TeamInfo.LEAGUE_NHL else TeamInfo.LEAGUE_CALGARY
            Prefs.setLeague(this, l)
            populate(l)
        }

        val radioPeriod = findViewById<RadioGroup>(R.id.radioPeriodLength)
        val radioDifficulty = findViewById<RadioGroup>(R.id.radioDifficulty)
        val radioArena = findViewById<RadioGroup>(R.id.radioArena)
        if (Prefs.arenaType(this) == com.tablehockey.game.model.ArenaType.WINTER_POND) {
            radioArena.check(R.id.arenaWinterPond)
        } else {
            radioArena.check(R.id.arenaIndoor)
        }
        AudioSliders.bind(findViewById(R.id.audioSliders), this)
        val checkAnthem = findViewById<android.widget.CheckBox>(R.id.checkAnthem)
        checkAnthem.isChecked = Prefs.anthemEnabled(this)
        checkAnthem.setOnCheckedChangeListener { _, on -> Prefs.setAnthemEnabled(this, on) }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            MusicManager.click(this)
            val homePos = spinnerHome.selectedItemPosition.coerceIn(0, teamIdx.size - 1)
            var awayPos = spinnerAway.selectedItemPosition.coerceIn(0, teamIdx.size - 1)
            if (homePos == awayPos) awayPos = (awayPos + 1) % teamIdx.size
            val home = teamIdx[homePos]
            val away = teamIdx[awayPos]
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
            val arena = if (radioArena.checkedRadioButtonId == R.id.arenaWinterPond) {
                com.tablehockey.game.model.ArenaType.WINTER_POND
            } else {
                com.tablehockey.game.model.ArenaType.INDOOR
            }
            Prefs.setArenaType(this, arena)
            val config = MatchConfig(
                mode = GameMode.SINGLE_PLAYER,
                homeTeam = home,
                awayTeam = away,
                periodLengthSeconds = periodLength,
                aiDifficulty = difficulty,
                soundEnabled = Prefs.soundEnabled(this),
                musicEnabled = Prefs.musicEnabled(this),
                arenaType = arena
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
