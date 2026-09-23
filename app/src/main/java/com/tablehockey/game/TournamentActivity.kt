package com.tablehockey.game

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.AiDifficulty
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.Prefs
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.model.TournamentMatch
import com.tablehockey.game.model.TournamentState

class TournamentActivity : AppCompatActivity() {

    private var state: TournamentState? = null

    private lateinit var layoutSetup: View
    private lateinit var layoutBracket: View
    private lateinit var spinnerSetupTeam: Spinner
    private lateinit var tvTournamentStatus: TextView
    private lateinit var tvMatchPrompt: TextView
    private lateinit var btnPlayMatch: Button
    private lateinit var btnReset: Button
    private lateinit var btnBack: Button
    private lateinit var layoutTrophy: View
    private lateinit var tvChampionName: TextView

    private val gameLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val homeScore = data.getIntExtra("HOME_SCORE", 0)
            val awayScore = data.getIntExtra("AWAY_SCORE", 0)
            val localWon = data.getBooleanExtra("LOCAL_WON", false)
            onMatchFinished(homeScore, awayScore, localWon)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament)

        layoutSetup = findViewById(R.id.layoutSetup)
        layoutBracket = findViewById(R.id.layoutBracket)
        spinnerSetupTeam = findViewById(R.id.spinnerSetupTeam)
        tvTournamentStatus = findViewById(R.id.tvTournamentStatus)
        tvMatchPrompt = findViewById(R.id.tvMatchPrompt)
        btnPlayMatch = findViewById(R.id.btnPlayMatch)
        btnReset = findViewById(R.id.btnReset)
        btnBack = findViewById(R.id.btnBack)
        layoutTrophy = findViewById(R.id.layoutTrophy)
        tvChampionName = findViewById(R.id.tvChampionName)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            TeamInfo.ALL.map { TeamInfo.label(it) }
        )
        spinnerSetupTeam.adapter = adapter
        spinnerSetupTeam.setSelection(TeamInfo.DEFAULT_HOME)

        findViewById<Button>(R.id.btnStartTournament).setOnClickListener {
            MusicManager.click(this)
            val selectedIdx = spinnerSetupTeam.selectedItemPosition
            state = TournamentState.createNew(selectedIdx)
            Prefs.saveTournament(this, state!!)
            updateUi()
        }

        btnBack.setOnClickListener {
            MusicManager.click(this)
            finish()
        }

        btnReset.setOnClickListener {
            MusicManager.click(this)
            Prefs.clearTournament(this)
            state = null
            updateUi()
        }

        btnPlayMatch.setOnClickListener {
            MusicManager.click(this)
            val s = state ?: return@setOnClickListener
            if (s.trophyWon || s.userEliminated) {
                Prefs.clearTournament(this)
                state = null
                updateUi()
            } else {
                startCurrentMatch()
            }
        }

        state = Prefs.getTournament(this)
        updateUi()
    }

    override fun onStart() {
        super.onStart()
        MusicManager.menuStarted(this)
    }

    override fun onStop() {
        super.onStop()
        MusicManager.menuStopped()
    }

    private fun updateUi() {
        val s = state
        if (s == null) {
            layoutSetup.visibility = View.VISIBLE
            layoutBracket.visibility = View.GONE
            btnPlayMatch.visibility = View.GONE
            btnReset.visibility = View.GONE
            tvTournamentStatus.text = "Select your club to begin the 8-team bracket"
            tvMatchPrompt.text = "No active tournament"
            return
        }

        layoutSetup.visibility = View.GONE
        layoutBracket.visibility = View.VISIBLE
        btnPlayMatch.visibility = View.VISIBLE
        btnReset.visibility = View.VISIBLE

        // Bind Quarterfinals
        bindMatchCard(findViewById(R.id.cardQf0), s.qf.getOrNull(0), s.userTeam)
        bindMatchCard(findViewById(R.id.cardQf1), s.qf.getOrNull(1), s.userTeam)
        bindMatchCard(findViewById(R.id.cardQf2), s.qf.getOrNull(2), s.userTeam)
        bindMatchCard(findViewById(R.id.cardQf3), s.qf.getOrNull(3), s.userTeam)

        // Bind Semifinals
        bindMatchCard(findViewById(R.id.cardSf0), s.sf.getOrNull(0), s.userTeam)
        bindMatchCard(findViewById(R.id.cardSf1), s.sf.getOrNull(1), s.userTeam)

        // Bind Finals
        bindMatchCard(findViewById(R.id.cardFinal), s.finals, s.userTeam)

        when {
            s.trophyWon -> {
                layoutTrophy.visibility = View.VISIBLE
                val champion = TeamInfo.byIndex(s.userTeam)
                tvChampionName.text = "${champion.fullName.uppercase()} CHAMPIONS!"
                tvTournamentStatus.text = "🏆 TOURNAMENT CHAMPION!"
                tvMatchPrompt.text = "Congratulations! You won the Championship Cup!"
                btnPlayMatch.text = "NEW TOURNAMENT"
            }
            s.userEliminated -> {
                layoutTrophy.visibility = View.GONE
                tvTournamentStatus.text = "ELIMINATED"
                tvMatchPrompt.text = "Tough loss. Your tournament run has ended."
                btnPlayMatch.text = "TRY AGAIN"
            }
            s.currentRound == 0 -> {
                layoutTrophy.visibility = View.GONE
                val opp = TeamInfo.byIndex(s.qf[0].team2)
                tvTournamentStatus.text = "ROUND 1: QUARTERFINALS"
                tvMatchPrompt.text = "Matchup: vs ${opp.fullName}"
                btnPlayMatch.text = "PLAY QUARTERFINAL"
            }
            s.currentRound == 1 -> {
                layoutTrophy.visibility = View.GONE
                val opp = TeamInfo.byIndex(s.sf[0].team2)
                tvTournamentStatus.text = "ROUND 2: SEMIFINALS"
                tvMatchPrompt.text = "Matchup: vs ${opp.fullName}"
                btnPlayMatch.text = "PLAY SEMIFINAL"
            }
            s.currentRound == 2 -> {
                layoutTrophy.visibility = View.GONE
                val opp = TeamInfo.byIndex(s.finals!!.team2)
                tvTournamentStatus.text = "CHAMPIONSHIP FINAL"
                tvMatchPrompt.text = "Championship Match: vs ${opp.fullName}"
                btnPlayMatch.text = "PLAY FINAL"
            }
        }
    }

    private fun bindMatchCard(view: View, match: TournamentMatch?, userTeam: Int) {
        if (match == null) {
            view.alpha = 0.35f
            val abbr1 = view.findViewById<TextView>(R.id.tvAbbr1)
            val name1 = view.findViewById<TextView>(R.id.tvName1)
            val score1 = view.findViewById<TextView>(R.id.tvScore1)
            val abbr2 = view.findViewById<TextView>(R.id.tvAbbr2)
            val name2 = view.findViewById<TextView>(R.id.tvName2)
            val score2 = view.findViewById<TextView>(R.id.tvScore2)
            abbr1.text = "TBD"; name1.text = ""; score1.text = "-"
            abbr2.text = "TBD"; name2.text = ""; score2.text = "-"
            return
        }

        view.alpha = 1.0f
        val t1 = TeamInfo.byIndex(match.team1)
        val t2 = TeamInfo.byIndex(match.team2)

        val color1 = view.findViewById<View>(R.id.colorTeam1)
        val abbr1 = view.findViewById<TextView>(R.id.tvAbbr1)
        val name1 = view.findViewById<TextView>(R.id.tvName1)
        val score1 = view.findViewById<TextView>(R.id.tvScore1)

        val color2 = view.findViewById<View>(R.id.colorTeam2)
        val abbr2 = view.findViewById<TextView>(R.id.tvAbbr2)
        val name2 = view.findViewById<TextView>(R.id.tvName2)
        val score2 = view.findViewById<TextView>(R.id.tvScore2)

        color1.setBackgroundColor(t1.primary)
        color2.setBackgroundColor(t2.primary)

        abbr1.text = t1.abbr
        name1.text = t1.name
        score1.text = if (match.score1 >= 0) match.score1.toString() else "-"

        abbr2.text = t2.abbr
        name2.text = t2.name
        score2.text = if (match.score2 >= 0) match.score2.toString() else "-"

        // Highlight user's team in gold
        if (match.team1 == userTeam) {
            abbr1.setTextColor(Color.parseColor("#FBBF24"))
        } else {
            abbr1.setTextColor(Color.parseColor("#F4F7FA"))
        }
        if (match.team2 == userTeam) {
            abbr2.setTextColor(Color.parseColor("#FBBF24"))
        } else {
            abbr2.setTextColor(Color.parseColor("#F4F7FA"))
        }

        // Highlight winner
        if (match.winner == match.team1) {
            score1.setTextColor(Color.parseColor("#34D399"))
            score2.setTextColor(Color.parseColor("#94A3B8"))
        } else if (match.winner == match.team2) {
            score2.setTextColor(Color.parseColor("#34D399"))
            score1.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            score1.setTextColor(Color.parseColor("#F4F7FA"))
            score2.setTextColor(Color.parseColor("#F4F7FA"))
        }
    }

    private fun startCurrentMatch() {
        val s = state ?: return
        val match = when (s.currentRound) {
            0 -> s.qf[0]
            1 -> s.sf[0]
            2 -> s.finals!!
            else -> return
        }

        val config = MatchConfig(
            mode = GameMode.SINGLE_PLAYER,
            homeTeam = match.team1,
            awayTeam = match.team2,
            periodLengthSeconds = 120,
            aiDifficulty = AiDifficulty.MEDIUM,
            soundEnabled = Prefs.soundEnabled(this),
            musicEnabled = Prefs.musicEnabled(this)
        )

        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(MatchConfig.EXTRA_KEY, config)
            putExtra("IS_TOURNAMENT", true)
        }
        gameLauncher.launch(intent)
    }

    private fun onMatchFinished(homeScore: Int, awayScore: Int, localWon: Boolean) {
        val s = state ?: return

        when (s.currentRound) {
            0 -> {
                val userMatch = s.qf[0]
                userMatch.score1 = homeScore
                userMatch.score2 = awayScore
                userMatch.winner = if (localWon) userMatch.team1 else userMatch.team2

                if (!localWon) {
                    s.userEliminated = true
                } else {
                    // Simulate other QFs
                    for (i in 1..3) TournamentState.simulateAiMatch(s.qf[i])
                    // Build Semifinals
                    s.sf.clear()
                    s.sf.add(TournamentMatch(s.qf[0].winner, s.qf[1].winner))
                    s.sf.add(TournamentMatch(s.qf[2].winner, s.qf[3].winner))
                    s.currentRound = 1
                }
            }
            1 -> {
                val userMatch = s.sf[0]
                userMatch.score1 = homeScore
                userMatch.score2 = awayScore
                userMatch.winner = if (localWon) userMatch.team1 else userMatch.team2

                if (!localWon) {
                    s.userEliminated = true
                } else {
                    // Simulate other SF
                    TournamentState.simulateAiMatch(s.sf[1])
                    // Build Final
                    s.finals = TournamentMatch(s.sf[0].winner, s.sf[1].winner)
                    s.currentRound = 2
                }
            }
            2 -> {
                val userMatch = s.finals!!
                userMatch.score1 = homeScore
                userMatch.score2 = awayScore
                userMatch.winner = if (localWon) userMatch.team1 else userMatch.team2

                if (localWon) {
                    s.trophyWon = true
                    s.currentRound = 3
                } else {
                    s.userEliminated = true
                }
            }
        }

        Prefs.saveTournament(this, s)
        updateUi()
    }
}
