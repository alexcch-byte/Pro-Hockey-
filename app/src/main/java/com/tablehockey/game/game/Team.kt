package com.tablehockey.game.game

import com.tablehockey.game.model.TeamInfo

/**
 * One club's six players plus its match stats. [attackDir] is +1 when the
 * team is shooting toward +x and flips each period.
 */
class Team(val id: Int, val info: TeamInfo, var attackDir: Float) {
    val skaters: List<Skater> = listOf(
        Skater(id, 0, Role.C, 9 + id * 10),
        Skater(id, 1, Role.LW, 17 + id * 10),
        Skater(id, 2, Role.RW, 21 + id * 10),
        Skater(id, 3, Role.LD, 4 + id * 10),
        Skater(id, 4, Role.RD, 7 + id * 10),
        Skater(id, 5, Role.G, 31 + id * 10)
    )
    val goalie: Skater get() = skaters[5]

    var score = 0
    var shots = 0

    /** x of this team's own goal line. */
    val ownGoalX: Float get() = -attackDir * Rink.GOAL_LINE_X
    /** x of the goal this team is attacking. */
    val targetGoalX: Float get() = attackDir * Rink.GOAL_LINE_X

    /** Attack-frame helpers: ax is positive toward the opponent's net. */
    fun toAttackX(worldX: Float) = worldX * attackDir
    fun toWorldX(attackX: Float) = attackX * attackDir
}
