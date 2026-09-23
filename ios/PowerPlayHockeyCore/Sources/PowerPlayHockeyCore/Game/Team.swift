/// One club's six players plus its match stats. [attackDir] is +1 when the
/// team is shooting toward +x and flips each period.
final class Team {
    let id: Int
    let info: TeamInfo
    var attackDir: Float

    let skaters: [Skater]

    init(id: Int, info: TeamInfo, attackDir: Float) {
        self.id = id
        self.info = info
        self.attackDir = attackDir
        self.skaters = [
            Skater(team: id, index: 0, role: .c, number: 9 + id * 10),
            Skater(team: id, index: 1, role: .lw, number: 17 + id * 10),
            Skater(team: id, index: 2, role: .rw, number: 21 + id * 10),
            Skater(team: id, index: 3, role: .ld, number: 4 + id * 10),
            Skater(team: id, index: 4, role: .rd, number: 7 + id * 10),
            Skater(team: id, index: 5, role: .g, number: 31 + id * 10)
        ]
    }

    var goalie: Skater { skaters[5] }

    var score = 0
    var shots = 0

    /// x of this team's own goal line.
    var ownGoalX: Float { -attackDir * Rink.GOAL_LINE_X }
    /// x of the goal this team is attacking.
    var targetGoalX: Float { attackDir * Rink.GOAL_LINE_X }

    /// Attack-frame helpers: ax is positive toward the opponent's net.
    func toAttackX(_ worldX: Float) -> Float { worldX * attackDir }
    func toWorldX(_ attackX: Float) -> Float { attackX * attackDir }
}
