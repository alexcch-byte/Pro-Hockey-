import CoreGraphics
import Foundation

/// drawShadow through drawPuck: skater/goalie body art and the puck.
/// Port of the "players" section of Renderer.kt.
extension Renderer {
    func drawShadow(_ canvas: GCanvas, _ s: Skater) {
        let r = CGFloat(s.radius) * 1.15 * Renderer.BODY_SCALE
        let x = CGFloat(s.x), y = CGFloat(s.y)
        tmpRect = CGRect(left: x - r + 0.3, top: y - r * 0.8 + 0.5, right: x + r + 0.3, bottom: y + r * 0.8 + 0.5)
        canvas.drawOval(tmpRect, shadowPaint)
    }

    func drawSkater(_ canvas: GCanvas, _ world: World, _ s: Skater, _ controlled: Bool, _ charge: CGFloat) {
        let info = world.teams[s.team].info
        let r = CGFloat(s.radius)
        let x = CGFloat(s.x), y = CGFloat(s.y)
        if controlled {
            let pulse = 1 + 0.06 * sin(animTime * 6)
            canvas.drawCircle(x, y, r * 2.2 * pulse, ringGlow)
            canvas.drawCircle(x, y, r * 2.2 * pulse, ringPaint)
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(CGFloat(s.facing) * 180 / .pi)
        let stunned = s.stunTimer > 0
        if stunned {
            // Flat on the ice.
            canvas.rotate(90)
            canvas.scale(1.45, 0.72)
        }
        if s.isGoalie { drawGoalieBody(canvas, s, info, goalieShaderFor(s.team)) }
        else { drawSkaterBody(canvas, s, info, skaterShaderFor(s.team)) }
        canvas.restore()

        // Upright number on the shoulders.
        numberPaint.color = info.text
        numberPaint.textSize = s.isGoalie ? 2.1 : 2.0
        canvas.drawText(String(s.number), x - cos(CGFloat(s.facing)) * 0.5, y - sin(CGFloat(s.facing)) * 0.5 + 0.7, numberPaint)

        if stunned { drawDizzyStars(canvas, s) }

        if controlled && charge > 0 && world.puck.carrier === s {
            tmpRect = CGRect(left: x - r * 1.9, top: y - r * 1.9, right: x + r * 1.9, bottom: y + r * 1.9)
            canvas.drawArc(tmpRect, -210, 240, false, meterBack)
            canvas.drawArc(tmpRect, -210, 240 * charge, false, meterFill)
        }
    }

    func drawDizzyStars(_ canvas: GCanvas, _ s: Skater) {
        let r = CGFloat(s.radius)
        let x = CGFloat(s.x), y = CGFloat(s.y)
        for i in 0...2 {
            let a = animTime * 5 + CGFloat(i) * 2.094
            let sx = x + cos(a) * r * 1.5
            let sy = y + sin(a) * r * 0.8 - r * 1.1
            let k: CGFloat = 0.32
            canvas.drawLine(sx - k, sy, sx + k, sy, starPaint)
            canvas.drawLine(sx, sy - k, sx, sy + k, starPaint)
            canvas.drawLine(sx - k * 0.6, sy - k * 0.6, sx + k * 0.6, sy + k * 0.6, starPaint)
            canvas.drawLine(sx - k * 0.6, sy + k * 0.6, sx + k * 0.6, sy - k * 0.6, starPaint)
        }
    }

    /// Draws the part of the segment between parameters t0 and t1.
    private func drawSegmentPortion(_ canvas: GCanvas, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ t0: CGFloat, _ t1: CGFloat, _ paint: GPaint) {
        canvas.drawLine(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1, paint)
    }

    private func drawGlove(_ canvas: GCanvas, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat, _ team: Int, _ info: TeamInfo) {
        glovePaint.color = gloveColorFor(team)
        tmpRect = CGRect(left: x - size, top: y - size * 0.85, right: x + size, bottom: y + size * 0.85)
        canvas.drawRoundRect(tmpRect, size * 0.45, size * 0.45, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.45, size * 0.45, gloveOutline)
        gloveCuff.color = info.secondary
        tmpRect = CGRect(left: x - size * 0.95, top: y - size * 0.8, right: x - size * 0.45, bottom: y + size * 0.8)
        canvas.drawRoundRect(tmpRect, size * 0.25, size * 0.25, gloveCuff)
    }

    private func drawSkaterBody(_ canvas: GCanvas, _ s: Skater, _ info: TeamInfo, _ shader: RadialGradientSpec) {
        let r = CGFloat(s.radius)
        canvas.scale(Renderer.BODY_SCALE, Renderer.BODY_SCALE)
        let moving = s.speed > 2
        let stride: CGFloat = moving ? sin(CGFloat(s.stride) * 1.3) : 0
        let bladeLocal = CGFloat(s.stickReach) / Renderer.BODY_SCALE

        // Skates: the pushing foot swings back and splays outward.
        for side: CGFloat in [-1, 1] {
            let phase = stride * side
            let push = max(0, -phase)
            let bx = -0.15 * r + phase * 0.42 * r
            let by = side * (0.72 * r + push * 0.4 * r)
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(side * (-6 + push * 32))
            skateBladePaint.strokeWidth = 0.09 * r
            canvas.drawLine(-0.62 * r, 0.05 * r, 0.62 * r, 0.05 * r, skateBladePaint)
            tmpRect = CGRect(left: -0.5 * r, top: -0.21 * r, right: 0.5 * r, bottom: 0.21 * r)
            canvas.drawRoundRect(tmpRect, 0.15 * r, 0.15 * r, bootPaint)
            bootLacePaint.strokeWidth = 0.05 * r
            canvas.drawLine(-0.25 * r, 0, 0.32 * r, 0, bootLacePaint)
            canvas.restore()
        }

        // Stick and arms; the whole group swings on a shot or pass.
        var kick: CGFloat = 0
        if s.swingTimer > 0 {
            let t = 1 - CGFloat(s.swingTimer) / 0.35
            kick = t < 0.4 ? -55 * (t / 0.4) : -55 + 110 * ((t - 0.4) / 0.6)
        }
        canvas.save()
        canvas.rotate(kick)
        let bX = 0.2 * r
        let bY = -0.95 * r
        let hX = bladeLocal - 0.5
        let hY: CGFloat = 0.3
        var dX = hX - bX
        var dY = hY - bY
        let len = max(hypot(dX, dY), 0.01)
        dX /= len; dY /= len
        let tX = bX + dX * 0.3 * r
        let tY = bY + dY * 0.3 * r
        let uDist = min(1.3 * r, len * 0.55)
        let uX = bX + dX * uDist
        let uY = bY + dY * uDist
        let s1X = -0.15 * r, s1Y = -0.8 * r
        let s2X = -0.15 * r, s2Y = 0.8 * r
        // Arms with sleeve stripes.
        armOutline.strokeWidth = 0.62 * r
        canvas.drawLine(s1X, s1Y, tX, tY, armOutline)
        canvas.drawLine(s2X, s2Y, uX, uY, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.5 * r
        canvas.drawLine(s1X, s1Y, tX, tY, armPaint)
        canvas.drawLine(s2X, s2Y, uX, uY, armPaint)
        sleevePaint.color = info.secondary
        sleevePaint.strokeWidth = 0.5 * r
        drawSegmentPortion(canvas, s1X, s1Y, tX, tY, 0.42, 0.6, sleevePaint)
        drawSegmentPortion(canvas, s2X, s2Y, uX, uY, 0.42, 0.6, sleevePaint)
        // Shaft with grip tape.
        shaftDark.strokeWidth = 0.2 * r
        canvas.drawLine(bX, bY, hX, hY, shaftDark)
        shaftCore.strokeWidth = 0.08 * r
        canvas.drawLine(bX, bY, hX, hY, shaftCore)
        tapePaint.strokeWidth = 0.22 * r
        canvas.drawLine(bX, bY, bX + dX * 0.4 * r, bY + dY * 0.4 * r, tapePaint)
        // Curved, taped blade.
        tmpPath.reset()
        tmpPath.moveTo(hX, hY)
        tmpPath.quadTo(bladeLocal + 0.05, 0.42, bladeLocal + 0.5, -0.25)
        bladeOutline.strokeWidth = 0.46
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.3
        canvas.drawPath(tmpPath, bladeTape)
        // Gloves on the shaft.
        drawGlove(canvas, tX, tY, 0.4 * r, s.team, info)
        drawGlove(canvas, uX, uY, 0.4 * r, s.team, info)
        canvas.restore()

        // Torso with shaded jersey, shoulder pads and yoke stripes.
        torsoPaint.shader = shader
        tmpRect = CGRect(left: -0.95 * r, top: -1.1 * r, right: 0.8 * r, bottom: 1.1 * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.15 * r, -0.95 * r, 0.36 * r, torsoPaint)
        canvas.drawCircle(-0.15 * r, 0.95 * r, 0.36 * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.3 * r
        canvas.drawLine(-0.5 * r, -0.95 * r, -0.5 * r, 0.95 * r, yokePaint)
        yokeThin.strokeWidth = 0.08 * r
        canvas.drawLine(-0.74 * r, -0.82 * r, -0.74 * r, 0.82 * r, yokeThin)

        // Helmet with visor.
        helmetPaint.color = helmetColorFor(s.team)
        let hx = 0.28 * r
        let hr = 0.58 * r
        canvas.drawCircle(hx, 0, hr, helmetPaint)
        canvas.drawCircle(hx, 0, hr, helmetOutline)
        tmpRect = CGRect(left: hx - hr, top: -hr, right: hx + hr, bottom: hr)
        canvas.drawArc(tmpRect, -50, 100, false, visorPaint)
        canvas.drawCircle(hx + 0.15 * r, -0.2 * r, 0.15 * r, glossPaint)
    }

    private func drawGoalieBody(_ canvas: GCanvas, _ g: Skater, _ info: TeamInfo, _ shader: RadialGradientSpec) {
        let r = CGFloat(g.radius)
        canvas.scale(Renderer.GOALIE_SCALE, Renderer.GOALIE_SCALE)
        let bladeLocal = CGFloat(g.stickReach) / Renderer.GOALIE_SCALE
        padStripe.color = info.primary

        // Leg pads: stacked flat across the crease in the butterfly, upright otherwise.
        if g.butterfly {
            tmpRect = CGRect(left: -0.35 * r, top: -1.6 * r, right: 0.9 * r, bottom: 1.6 * r)
            canvas.drawRoundRect(tmpRect, 0.35 * r, 0.35 * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.35 * r, 0.35 * r, padOutline)
            for yy: CGFloat in [-0.8, 0.8] {
                tmpRect = CGRect(left: -0.3 * r, top: yy * r - 0.14 * r, right: 0.85 * r, bottom: yy * r + 0.14 * r)
                canvas.drawRect(tmpRect, padStripe)
            }
            canvas.drawLine(-0.3 * r, 0, 0.85 * r, 0, padOutline)
        } else {
            for side: CGFloat in [-1, 1] {
                let inner = side * 0.32 * r
                let outer = side * 1.12 * r
                tmpRect = CGRect(left: -0.7 * r, top: min(inner, outer), right: 0.72 * r, bottom: max(inner, outer))
                canvas.drawRoundRect(tmpRect, 0.3 * r, 0.3 * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.3 * r, 0.3 * r, padOutline)
                tmpRect = CGRect(left: -0.2 * r, top: min(inner, outer) + 0.08 * r, right: 0.08 * r, bottom: max(inner, outer) - 0.08 * r)
                canvas.drawRect(tmpRect, padStripe)
            }
        }

        // Goalie stick: paddle down to a wide blade on the ice.
        shaftDark.strokeWidth = 0.34 * r
        canvas.drawLine(0.45 * r, 1.0 * r, bladeLocal - 0.9, 0.85, shaftDark)
        shaftCore.strokeWidth = 0.12 * r
        canvas.drawLine(0.45 * r, 1.0 * r, bladeLocal - 0.9, 0.85, shaftCore)
        tmpPath.reset()
        tmpPath.moveTo(bladeLocal - 0.95, 0.9)
        tmpPath.quadTo(bladeLocal, 0.7, bladeLocal + 0.5, 0.15)
        bladeOutline.strokeWidth = 0.55
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.38
        canvas.drawPath(tmpPath, bladeTape)

        // Arms.
        armOutline.strokeWidth = 0.68 * r
        canvas.drawLine(-0.1 * r, -0.9 * r, 0.8 * r, -1.15 * r, armOutline)
        canvas.drawLine(-0.1 * r, 0.9 * r, 0.75 * r, 1.05 * r, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.55 * r
        canvas.drawLine(-0.1 * r, -0.9 * r, 0.8 * r, -1.15 * r, armPaint)
        canvas.drawLine(-0.1 * r, 0.9 * r, 0.75 * r, 1.05 * r, armPaint)

        // Chest protector under the jersey.
        torsoPaint.shader = shader
        tmpRect = CGRect(left: -1.0 * r, top: -1.2 * r, right: 0.85 * r, bottom: 1.2 * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.2 * r, -1.05 * r, 0.4 * r, torsoPaint)
        canvas.drawCircle(-0.2 * r, 1.05 * r, 0.4 * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.32 * r
        canvas.drawLine(-0.5 * r, -1.0 * r, -0.5 * r, 1.0 * r, yokePaint)

        // Catching glove (trapper) and blocker.
        canvas.drawCircle(0.95 * r, -1.25 * r, 0.52 * r, leatherPaint)
        canvas.drawCircle(0.95 * r, -1.25 * r, 0.52 * r, gloveOutline)
        canvas.drawCircle(1.02 * r, -1.3 * r, 0.3 * r, leatherLight)
        canvas.drawLine(0.7 * r, -1.25 * r, 1.2 * r, -1.25 * r, cagePaint)
        canvas.save()
        canvas.translate(0.95 * r, 1.15 * r)
        canvas.rotate(-15)
        tmpRect = CGRect(left: -0.36 * r, top: -0.5 * r, right: 0.36 * r, bottom: 0.5 * r)
        canvas.drawRoundRect(tmpRect, 0.12 * r, 0.12 * r, padPaint)
        canvas.drawRoundRect(tmpRect, 0.12 * r, 0.12 * r, gloveOutline)
        tmpRect = CGRect(left: -0.36 * r, top: -0.1 * r, right: 0.36 * r, bottom: 0.1 * r)
        canvas.drawRect(tmpRect, padStripe)
        canvas.restore()

        // Mask with cage.
        maskPaint.color = info.secondary
        let mx = 0.32 * r
        let mr = 0.62 * r
        canvas.drawCircle(mx, 0, mr, maskPaint)
        canvas.drawCircle(mx, 0, mr, helmetOutline)
        for yy: CGFloat in [-0.28, 0, 0.28] {
            canvas.drawLine(mx + 0.15 * r, yy * r, mx + 0.58 * r, yy * r * 0.8, cagePaint)
        }
        canvas.drawLine(mx + 0.28 * r, -0.36 * r, mx + 0.28 * r, 0.36 * r, cagePaint)
        canvas.drawLine(mx + 0.46 * r, -0.3 * r, mx + 0.46 * r, 0.3 * r, cagePaint)
        canvas.drawCircle(mx + 0.05 * r, -0.28 * r, 0.13 * r, glossPaint)
    }

    func drawPuck(_ canvas: GCanvas, _ p: Puck) {
        let sp = CGFloat(p.speed)
        let x = CGFloat(p.x), y = CGFloat(p.y)
        if p.carrier == nil && sp > 35 {
            let len = min(4, sp / 25)
            canvas.drawLine(x, y, x - CGFloat(p.vx) / sp * len, y - CGFloat(p.vy) / sp * len, puckTrail)
        }
        canvas.drawCircle(x + 0.15, y + 0.2, 0.85, shadowPaint)
        canvas.drawCircle(x, y, 0.95, puckHalo)
        canvas.drawCircle(x, y, 0.8, puckPaint)
        canvas.drawCircle(x, y, 0.55, puckRim)
    }
}
