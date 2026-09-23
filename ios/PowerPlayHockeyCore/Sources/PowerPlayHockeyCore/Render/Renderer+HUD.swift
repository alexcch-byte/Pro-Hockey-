import CoreGraphics
import Foundation

/// Scoreboard, banner, on-screen controls, shootout controls and pause button, all drawn in
/// screen space (outside the camera transform). Port of the HUD section of Renderer.kt.
extension Renderer {
    private func dp(_ v: CGFloat) -> CGFloat { v * density }

    func drawScoreboard(_ canvas: GCanvas, _ w: World) {
        let cx = CGFloat(camera.screenW) / 2
        let width = dp(330)
        let height = w.isShootout ? dp(54) : dp(46)
        let top = dp(8)
        tmpRect = CGRect(left: cx - width / 2, top: top, right: cx + width / 2, bottom: top + height)
        canvas.drawRoundRect(tmpRect, dp(10), dp(10), hudBack)

        let boxW = dp(58)
        hudTeamBox.color = w.teams[0].info.primary
        tmpRect = CGRect(left: cx - width / 2 + dp(4), top: top + dp(4), right: cx - width / 2 + dp(4) + boxW, bottom: top + height - dp(4))
        canvas.drawRoundRect(tmpRect, dp(8), dp(8), hudTeamBox)
        hudText.textSize = dp(17)
        hudText.color = w.teams[0].info.text
        canvas.drawText(w.teams[0].info.abbr, tmpRect.midX, tmpRect.midY + dp(6), hudText)

        hudTeamBox.color = w.teams[1].info.primary
        tmpRect = CGRect(left: cx + width / 2 - dp(4) - boxW, top: top + dp(4), right: cx + width / 2 - dp(4), bottom: top + height - dp(4))
        canvas.drawRoundRect(tmpRect, dp(8), dp(8), hudTeamBox)
        hudText.color = w.teams[1].info.text
        canvas.drawText(w.teams[1].info.abbr, tmpRect.midX, tmpRect.midY + dp(6), hudText)

        hudText.color = HexColor.white
        hudText.textSize = dp(24)
        let scoreY = w.isShootout ? top + dp(27) : top + dp(33)
        canvas.drawText(String(w.teams[0].score), cx - dp(92), scoreY, hudText)
        canvas.drawText(String(w.teams[1].score), cx + dp(92), scoreY, hudText)

        if w.isShootout {
            hudText.textSize = dp(18)
            let timerStr = String(format: "%.1fs", max(w.shootoutTimer, 0))
            canvas.drawText(timerStr, cx, top + dp(21), hudText)
            hudSmall.textSize = dp(10)
            let stText = w.shootoutRound <= 5 ? "ROUND \(w.shootoutRound)" : "SUDDEN DEATH"
            canvas.drawText(stText, cx, top + dp(35), hudSmall)
            let shooterAbbr = w.teams[w.shootoutTurn].info.abbr
            canvas.drawText("\(shooterAbbr) SHOOTING", cx, top + dp(47), hudSmall)

            // 5-round shootout indicators positioned directly under team scores
            let dotR = dp(3.2)
            let dotSpacing = dp(8.5)
            let dotY = top + dp(42)

            let t0StartX = (cx - dp(92)) - 2 * dotSpacing
            for r in 0..<5 {
                let res = w.shootoutAttempts[0][r]
                let dx = t0StartX + CGFloat(r) * dotSpacing
                if res == 1 {
                    shootoutDotFill.color = HexColor.argb("#22C55E")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else if res == 2 {
                    shootoutDotFill.color = HexColor.argb("#EF4444")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else {
                    shootoutDotStroke.color = HexColor.argb("#64748B")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotStroke)
                }
            }

            let t1StartX = (cx + dp(92)) - 2 * dotSpacing
            for r in 0..<5 {
                let res = w.shootoutAttempts[1][r]
                let dx = t1StartX + CGFloat(r) * dotSpacing
                if res == 1 {
                    shootoutDotFill.color = HexColor.argb("#22C55E")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else if res == 2 {
                    shootoutDotFill.color = HexColor.argb("#EF4444")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else {
                    shootoutDotStroke.color = HexColor.argb("#64748B")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotStroke)
                }
            }
        } else {
            hudText.textSize = dp(19)
            canvas.drawText(w.clockText(), cx, top + dp(23), hudText)
            hudSmall.textSize = dp(11)
            canvas.drawText(w.periodText() + (w.overtime ? "  SUDDEN DEATH" : "  PERIOD"), cx, top + dp(39), hudSmall)

            // Shots on goal
            hudSmall.textSize = dp(11)
            canvas.drawText("SHOTS  " + String(w.teams[0].shots) + " - " + String(w.teams[1].shots), cx, top + height + dp(14), hudSmall)
        }

        // Flame indicators for on-fire teams
        if w.isOnFire(0) {
            hudSmall.textSize = dp(11)
            hudSmall.color = HexColor.argb("#F97316")
            canvas.drawText("🔥 \(Int(w.fireTimer[0]))s", cx - width / 2 + dp(33), top - dp(3), hudSmall)
            hudSmall.color = HexColor.argb("#CBD5E1")
        }
        if w.isOnFire(1) {
            hudSmall.textSize = dp(11)
            hudSmall.color = HexColor.argb("#F97316")
            canvas.drawText("🔥 \(Int(w.fireTimer[1]))s", cx + width / 2 - dp(33), top - dp(3), hudSmall)
            hudSmall.color = HexColor.argb("#CBD5E1")
        }

        // Power play or empty net badge
        if w.penaltyTeam != -1 {
            let advTeam = w.opponent(w.penaltyTeam)
            let pTimer = max(Int(w.penaltyTimer), 0)
            let m = pTimer / 60
            let s = pTimer % 60
            let ppStr = String(format: "%@ PP %d:%02d", advTeam.info.abbr, m, s)
            let badgeW = dp(104)
            let badgeH = dp(17)
            let badgeY = top + height + dp(20)
            tmpRect = CGRect(left: cx - badgeW / 2, top: badgeY, right: cx + badgeW / 2, bottom: badgeY + badgeH)
            ppBadgeBack.color = advTeam.info.primary
            ppBadgeBorder.strokeWidth = dp(1.5)
            canvas.drawRoundRect(tmpRect, dp(6), dp(6), ppBadgeBack)
            canvas.drawRoundRect(tmpRect, dp(6), dp(6), ppBadgeBorder)
            ppBadgeText.textSize = dp(10.5)
            ppBadgeText.color = advTeam.info.text
            canvas.drawText(ppStr, cx, badgeY + dp(12.5), ppBadgeText)
        } else if w.goaliePulled[0] || w.goaliePulled[1] {
            let pulledId = w.goaliePulled[0] ? 0 : 1
            let pTeam = w.teams[pulledId]
            let enStr = "\(pTeam.info.abbr} EMPTY NET"
            let badgeW = dp(108)
            let badgeH = dp(17)
            let badgeY = top + height + dp(20)
            tmpRect = CGRect(left: cx - badgeW / 2, top: badgeY, right: cx + badgeW / 2, bottom: badgeY + badgeH)
            ppBadgeBack.color = HexColor.argb("#B91C1C")
            ppBadgeBorder.strokeWidth = dp(1.5)
            canvas.drawRoundRect(tmpRect, dp(6), dp(6), ppBadgeBack)
            canvas.drawRoundRect(tmpRect, dp(6), dp(6), ppBadgeBorder)
            ppBadgeText.textSize = dp(10)
            ppBadgeText.color = HexColor.white
            canvas.drawText(enStr, cx, badgeY + dp(12.5), ppBadgeText)
        }
    }

    func drawBanner(_ canvas: GCanvas, _ w: World) {
        guard let text = w.banner else { return }
        let alpha: CGFloat = w.bannerTimer < 0.4 ? min(max(CGFloat(w.bannerTimer) / 0.4, 0), 1) : 1
        let cy = CGFloat(camera.screenH) * 0.36
        let h = dp(96)
        bannerBack.alpha = Int(170 * alpha)
        tmpRect = CGRect(left: 0, top: cy - h / 2, right: CGFloat(camera.screenW), bottom: cy + h / 2)
        canvas.drawRect(tmpRect, bannerBack)
        bannerText.textSize = dp(text.count > 12 ? 30 : 44)
        bannerText.alpha = Int(255 * alpha)
        canvas.drawText(text, CGFloat(camera.screenW) / 2, cy + dp(w.bannerSub != nil ? 4 : 14), bannerText)
        if let sub = w.bannerSub {
            bannerSub.textSize = dp(16)
            bannerSub.alpha = Int(255 * alpha)
            canvas.drawText(sub, CGFloat(camera.screenW) / 2, cy + dp(30), bannerSub)
        }
    }

    func drawControls(_ canvas: GCanvas, _ w: World, _ localTeam: Int, _ c: TouchControlsState) {
        // Joystick.
        let jr = c.joyRadius
        let ax = c.joyActive ? c.joyAnchorX : c.joyRestX
        let ay = c.joyActive ? c.joyAnchorY : c.joyRestY
        ctrlBase.alpha = c.joyActive ? 90 : 45
        ctrlRing.strokeWidth = dp(2)
        ctrlRing.alpha = c.joyActive ? 160 : 80
        canvas.drawCircle(ax, ay, jr, ctrlBase)
        canvas.drawCircle(ax, ay, jr, ctrlRing)
        let kx = c.joyActive ? c.joyKnobX : ax
        let ky = c.joyActive ? c.joyKnobY : ay
        ctrlKnob.alpha = c.joyActive ? 220 : 110
        canvas.drawCircle(kx, ky, jr * 0.42, ctrlKnob)

        let controlled = localTeam >= 0 ? w.controlledSkater(localTeam) : nil
        let isGoalie = controlled?.isGoalie == true

        if isGoalie {
            drawButton(canvas, c.shootX, c.shootY, c.shootR, "BUTTERFLY", "5-hole", c.shootDown, HexColor.argb("#DC2626"))
            drawButton(canvas, c.passX, c.passY, c.passR, "POKE", "stick", c.passDown, HexColor.argb("#2563EB"))
            drawButton(canvas, c.hitX, c.hitY, c.hitR, "PAD STACK", "sprawl", c.hitDown, HexColor.argb("#D97706"))
        } else {
            let hasPuck = localTeam >= 0 && w.puck.carrier != nil && w.puck.carrier === controlled
            drawButton(canvas, c.shootX, c.shootY, c.shootR, "SHOOT", hasPuck ? "" : "poke", c.shootDown, HexColor.argb("#DC2626"))
            drawButton(canvas, c.passX, c.passY, c.passR, "PASS", hasPuck ? "" : "switch", c.passDown, HexColor.argb("#2563EB"))
            drawButton(canvas, c.hitX, c.hitY, c.hitR, "HIT", "", c.hitDown, HexColor.argb("#D97706"))
            if c.shootDown && hasPuck {
                let charge = CGFloat(c.currentCharge())
                chargeArc.strokeWidth = dp(5)
                tmpRect = CGRect(left: c.shootX - c.shootR - dp(6), top: c.shootY - c.shootR - dp(6), right: c.shootX + c.shootR + dp(6), bottom: c.shootY + c.shootR + dp(6))
                canvas.drawArc(tmpRect, -90, 360 * charge, false, chargeArc)
            }
        }
    }

    private func drawButton(_ canvas: GCanvas, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ label: String, _ sub: String, _ down: Bool, _ color: UInt32) {
        btnFill.color = color
        btnFill.alpha = down ? 230 : 120
        canvas.drawCircle(x, y, r, btnFill)
        ctrlRing.strokeWidth = dp(2)
        ctrlRing.alpha = down ? 255 : 150
        canvas.drawCircle(x, y, r, ctrlRing)
        btnText.textSize = r * 0.42
        btnText.alpha = 255
        canvas.drawText(label, x, y + (sub.isEmpty ? r * 0.15 : r * 0.02), btnText)
        if !sub.isEmpty {
            btnText.textSize = r * 0.26
            btnText.alpha = 200
            canvas.drawText(sub, x, y + r * 0.42, btnText)
        }
    }

    func drawPauseButton(_ canvas: GCanvas) {
        let s = dp(40)
        let x = CGFloat(camera.screenW) - s - dp(10)
        let y = dp(10)
        tmpRect = CGRect(left: x, top: y, right: x + s, bottom: y + s)
        canvas.drawRoundRect(tmpRect, dp(8), dp(8), hudBack)
        hudText.textSize = dp(18)
        hudText.color = HexColor.white
        canvas.drawText("II", x + s / 2, y + s * 0.68, hudText)
    }

    func isPauseHit(_ x: CGFloat, _ y: CGFloat) -> Bool {
        let s = dp(40)
        let px = CGFloat(camera.screenW) - s - dp(10)
        let py = dp(10)
        return x >= px - dp(8) && x <= px + s + dp(8) && y >= py - dp(8) && y <= py + s + dp(8)
    }

    func drawShootoutControls(_ canvas: GCanvas, _ w: World, _ localTeam: Int) {
        guard w.isShootout, w.phase == .play, localTeam >= 0 else { return }
        let shooterTeamId = w.shootoutTurn

        if shooterTeamId == localTeam {
            guard let shooter = w.controlledSkater(localTeam), hypot(shooter.x, shooter.y) <= 12 else { return }

            let bw = dp(154)
            let bh = dp(42)
            let bx = dp(14)
            let by = dp(10)
            switchShooterRect = CGRect(left: bx, top: by, right: bx + bw, bottom: by + bh)

            canvas.drawRoundRect(switchShooterRect, dp(8), dp(8), switchShooterPaint)
            switchShooterBorder.strokeWidth = dp(1.5)
            canvas.drawRoundRect(switchShooterRect, dp(8), dp(8), switchShooterBorder)

            switchShooterTitle.textSize = dp(9.5)
            canvas.drawText("TAP TO CHANGE SHOOTER", bx + bw / 2, by + dp(14), switchShooterTitle)

            let roleStr = shooter.role.label
            switchShooterSub.textSize = dp(13)
            canvas.drawText("🔁 \(roleStr) #\(shooter.number)", bx + bw / 2, by + dp(32), switchShooterSub)
        } else {
            // Defending turn!
            let bw = dp(130)
            let bh = dp(32)
            let bx = dp(14)
            let by = dp(10)
            switchShooterRect = .zero
            tmpRect = CGRect(left: bx, top: by, right: bx + bw, bottom: by + bh)

            canvas.drawRoundRect(tmpRect, dp(6), dp(6), switchShooterPaint)
            switchShooterBorder.strokeWidth = dp(1.2)
            canvas.drawRoundRect(tmpRect, dp(6), dp(6), switchShooterBorder)

            switchShooterTitle.textSize = dp(10)
            canvas.drawText("DEFENDING GOALIE", bx + bw / 2, by + dp(14), switchShooterTitle)
            switchShooterSub.textSize = dp(12)
            let gNum = w.teams[localTeam].goalie.number
            canvas.drawText("YOU ARE GOALIE #\(gNum)", bx + bw / 2, by + dp(26), switchShooterSub)
        }
    }

    func isSwitchShooterHit(_ x: CGFloat, _ y: CGFloat, _ w: World, _ localTeam: Int) -> Bool {
        guard w.isShootout, w.phase == .play, localTeam >= 0, w.shootoutTurn == localTeam else { return false }
        guard let shooter = w.controlledSkater(localTeam), hypot(shooter.x, shooter.y) <= 12 else { return false }
        if switchShooterRect.contains(CGPoint(x: x, y: y)) { return true }
        let sx = CGFloat(camera.toScreenX(shooter.x))
        let sy = CGFloat(camera.toScreenY(shooter.y))
        return hypot(x - sx, y - sy) <= dp(45)
    }
}
