package com.tablehockey.game.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import kotlin.random.Random

data class TournamentMatch(
    val team1: Int,
    val team2: Int,
    var score1: Int = -1,
    var score2: Int = -1,
    var winner: Int = -1
) : Serializable {
    val isPlayed: Boolean get() = winner != -1

    fun toJson(): JSONObject = JSONObject().apply {
        put("t1", team1)
        put("t2", team2)
        put("s1", score1)
        put("s2", score2)
        put("w", winner)
    }

    companion object {
        fun fromJson(json: JSONObject): TournamentMatch {
            return TournamentMatch(
                team1 = json.getInt("t1"),
                team2 = json.getInt("t2"),
                score1 = json.optInt("s1", -1),
                score2 = json.optInt("s2", -1),
                winner = json.optInt("w", -1)
            )
        }
    }
}

data class TournamentState(
    val userTeam: Int,
    val league: String,
    var currentRound: Int = 0, // 0 = QF, 1 = SF, 2 = Final, 3 = Complete
    val qf: MutableList<TournamentMatch> = mutableListOf(),
    val sf: MutableList<TournamentMatch> = mutableListOf(),
    var finals: TournamentMatch? = null,
    var userEliminated: Boolean = false,
    var trophyWon: Boolean = false
) : Serializable {

    fun toJson(): String {
        val root = JSONObject()
        root.put("userTeam", userTeam)
        root.put("league", league)
        root.put("currentRound", currentRound)
        root.put("userEliminated", userEliminated)
        root.put("trophyWon", trophyWon)

        val qfArray = JSONArray()
        for (m in qf) qfArray.put(m.toJson())
        root.put("qf", qfArray)

        val sfArray = JSONArray()
        for (m in sf) sfArray.put(m.toJson())
        root.put("sf", sfArray)

        if (finals != null) {
            root.put("finals", finals!!.toJson())
        }
        return root.toString()
    }

    companion object {
        fun fromJson(str: String): TournamentState? {
            return try {
                val root = JSONObject(str)
                val state = TournamentState(
                    userTeam = root.getInt("userTeam"),
                    league = root.getString("league"),
                    currentRound = root.getInt("currentRound"),
                    userEliminated = root.optBoolean("userEliminated", false),
                    trophyWon = root.optBoolean("trophyWon", false)
                )
                val qfArray = root.getJSONArray("qf")
                for (i in 0 until qfArray.length()) {
                    state.qf.add(TournamentMatch.fromJson(qfArray.getJSONObject(i)))
                }
                val sfArray = root.getJSONArray("sf")
                for (i in 0 until sfArray.length()) {
                    state.sf.add(TournamentMatch.fromJson(sfArray.getJSONObject(i)))
                }
                if (root.has("finals")) {
                    state.finals = TournamentMatch.fromJson(root.getJSONObject("finals"))
                }
                state
            } catch (e: Exception) {
                null
            }
        }

        fun createNew(userTeamIdx: Int, rng: Random = Random): TournamentState {
            val userTeam = TeamInfo.byIndex(userTeamIdx)
            val league = userTeam.league
            val allInLeague = TeamInfo.ALL.indices.filter { TeamInfo.byIndex(it).league == league && it != userTeamIdx }.shuffled(rng)
            val opponents = allInLeague.take(7)

            val state = TournamentState(userTeam = userTeamIdx, league = league)

            state.qf.add(TournamentMatch(userTeamIdx, opponents[0]))
            state.qf.add(TournamentMatch(opponents[1], opponents[2]))
            state.qf.add(TournamentMatch(opponents[3], opponents[4]))
            state.qf.add(TournamentMatch(opponents[5], opponents[6]))

            return state
        }

        fun simulateAiMatch(match: TournamentMatch, rng: Random = Random) {
            var s1 = rng.nextInt(1, 5)
            var s2 = rng.nextInt(1, 5)
            if (s1 == s2) {
                if (rng.nextBoolean()) s1++ else s2++
            }
            match.score1 = s1
            match.score2 = s2
            match.winner = if (s1 > s2) match.team1 else match.team2
        }
    }
}
