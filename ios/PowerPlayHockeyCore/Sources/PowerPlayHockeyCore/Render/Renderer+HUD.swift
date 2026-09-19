import CoreGraphics

/// Scoreboard, banner, on-screen controls and pause button, all drawn in
/// screen space (outside the camera transform). Port of the HUD section of
/// Renderer.kt.
extension Renderer {
    private func dp(_ v: CGFloat) -> CGFloat { v * density }

    func drawScoreboard(_ canvas: GCanvas, _ w: World) {
        let cx = CGFloat(camera.screenW) / 2
        let width = dp(330)
        let height = dp(46)
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
        canvas.drawText(String(w.teams[0].score), cx - dp(92), top + dp(33), hudText)
        canvas.drawText(String(w.teams[1].score), cx + dp(92), top + dp(33), hudText)

        hudText.textSize = dp(19)
        canvas.drawText(w.clockText(), cx, top + dp(23), hudText)
        hudSmall.textSize = dp(11)
        canvas.drawText(w.periodText() + (w.overtime ? "  SUDDEN DEATH" : "  PERIOD"), cx, top + dp(39), hudSmall)

        hudSmall.textSize = dp(11)
        canvas.drawText("SHOTS  " + String(w.teams[0].shots) + " - " + String(w.teams[1].shots), cx, top + height + dp(14), hudSmall)
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

        let hasPuck = localTeam >= 0 && w.puck.carrier != nil && w.puck.carrier === w.controlledSkater(localTeam)
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
}
