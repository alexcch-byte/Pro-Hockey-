import CoreGraphics
import Foundation

/// drawShadow through drawPuck: skater/goalie body art, sprite visual overhaul and the puck.
/// Port of the "players" section of Renderer.kt.
extension Renderer {
    func drawShadow(_ canvas: GCanvas, _ s: Skater) {
        let r = CGFloat(s.radius) * 1.15 * Renderer.BODY_SCALE
        let x = CGFloat(s.x), y = CGFloat(s.y)
        // Ambient soft cast shadow
        tmpRect = CGRect(left: x - r + 0.35, top: y - r * 0.78 + 0.55, right: x + r + 0.35, bottom: y + r * 0.78 + 0.55)
        canvas.drawOval(tmpRect, shadowPaint)
        // Tight contact shadow right under skates/body
        tmpRect = CGRect(left: x - r * 0.7, top: y - r * 0.48 + 0.2, right: x + r * 0.7, bottom: y + r * 0.48 + 0.2)
        canvas.drawOval(tmpRect, contactShadow)
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
        var rot = CGFloat(s.facing) * 180 / .pi
        if s.dekeTimer > 0 {
            rot += CGFloat(s.dekeDir) * (CGFloat(s.dekeTimer) / 0.38) * 22
        }
        // Dynamic edge carving / banking lean into turns
        let latSpd = -CGFloat(s.vx) * sin(CGFloat(s.facing)) + CGFloat(s.vy) * cos(CGFloat(s.facing))
        let bankAngle = min(max(latSpd * 1.25, -16), 16)
        canvas.rotate(rot + bankAngle)

        let stunned = s.stunTimer > 0
        if stunned {
            // Flat on the ice.
            canvas.rotate(90)
            canvas.scale(1.45, 0.72)
        }
        if s.isGoalie {
            drawGoalieBody(canvas, s, info, goalieShaderFor(s.team))
        } else {
            drawSkaterBody(canvas, s, info, skaterShaderFor(s.team), controlled ? charge : 0)
        }
        canvas.restore()

        if world.isOnFire(s.team) {
            // Flickering fire particles trailing behind the skater
            for k in 0...3 {
                let fPhase = (animTime * 9 + CGFloat(k) * 0.25 + CGFloat(s.index) * 0.37).truncatingRemainder(dividingBy: 1.0)
                let offBack = 0.8 + fPhase * 1.5
                let jiggle = sin(animTime * 16 + CGFloat(k) * 2.3) * 0.32
                let fx = x - cos(CGFloat(s.facing)) * offBack - sin(CGFloat(s.facing)) * jiggle
                let fy = y - sin(CGFloat(s.facing)) * offBack + cos(CGFloat(s.facing)) * jiggle
                let fr = max(0.36 * (1 - fPhase * 0.7), 0.08)
                let alpha = min(max(Int(235 * (1 - fPhase)), 0), 255)
                fireEmberPaint.color = k % 2 == 0 ? HexColor.argb(alpha, 255, 90, 10) : HexColor.argb(alpha, 255, 210, 30)
                canvas.drawCircle(fx, fy, fr, fireEmberPaint)
            }
        }

        // Pro dual-tone drop shadow jersey numbers
        let numX = x - cos(CGFloat(s.facing)) * 0.5
        let numY = y - sin(CGFloat(s.facing)) * 0.5 + 0.7
        numberShadowPaint.textSize = s.isGoalie ? 2.1 : 2.0
        canvas.drawText(String(s.number), numX, numY, numberShadowPaint)
        numberPaint.color = info.text
        numberPaint.textSize = s.isGoalie ? 2.1 : 2.0
        canvas.drawText(String(s.number), numX, numY, numberPaint)

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

    private func drawSegmentPortion(_ canvas: GCanvas, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ t0: CGFloat, _ t1: CGFloat, _ paint: GPaint) {
        canvas.drawLine(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1, paint)
    }

    private func drawGlove(_ canvas: GCanvas, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat, _ team: Int, _ info: TeamInfo) {
        // Leather palm underneath
        tmpRect = CGRect(left: x - size * 0.72, top: y - size * 0.68, right: x + size * 0.72, bottom: y + size * 0.68)
        canvas.drawRoundRect(tmpRect, size * 0.28, size * 0.28, glovePalmPaint)

        // Main glove shell with team primary color
        glovePaint.color = gloveColorFor(team)
        tmpRect = CGRect(left: x - size, top: y - size * 0.85, right: x + size, bottom: y + size * 0.85)
        canvas.drawRoundRect(tmpRect, size * 0.45, size * 0.45, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.45, size * 0.45, gloveOutline)

        // 4-roll segmented finger rolls across the backhand
        let rollStep = size * 0.36
        for i: CGFloat in [-1, 0, 1] {
            let rx = x + i * rollStep * 0.65
            canvas.drawLine(rx, y - size * 0.62, rx, y + size * 0.62, gloveRollPaint)
        }

        // Flared protective wrist cuff in team secondary color with piping
        gloveCuff.color = info.secondary
        tmpRect = CGRect(left: x - size * 1.05, top: y - size * 0.82, right: x - size * 0.45, bottom: y + size * 0.82)
        canvas.drawRoundRect(tmpRect, size * 0.22, size * 0.22, gloveCuff)
        canvas.drawRoundRect(tmpRect, size * 0.22, size * 0.22, gloveOutline)

        // Anatomical locked thumb guard
        tmpRect = CGRect(left: x + size * 0.2, top: y - size * 0.95, right: x + size * 0.82, bottom: y - size * 0.4)
        canvas.drawRoundRect(tmpRect, size * 0.18, size * 0.18, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.18, size * 0.18, gloveOutline)
    }

    private func drawSkaterBody(_ canvas: GCanvas, _ s: Skater, _ info: TeamInfo, _ shader: RadialGradientSpec, _ charge: CGFloat) {
        let r = CGFloat(s.radius)
        canvas.scale(Renderer.BODY_SCALE, Renderer.BODY_SCALE)
        let moving = s.speed > 2
        let stride: CGFloat = moving ? sin(CGFloat(s.stride) * 1.3) : 0
        let bladeLocal = CGFloat(s.stickReach) / Renderer.BODY_SCALE

        // Skates: contoured boot, white TUUK holder, stainless runner with glints
        for side: CGFloat in [-1, 1] {
            let phase = stride * side
            let push = max(0, -phase)
            let bx = -0.15 * r + phase * 0.42 * r
            let by = side * (0.72 * r + push * 0.4 * r)
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(side * (-6 + push * 32))

            // White TUUK holder
            tmpRect = CGRect(left: -0.52 * r, top: 0.01 * r, right: 0.52 * r, bottom: 0.12 * r)
            canvas.drawRoundRect(tmpRect, 0.05 * r, 0.05 * r, holderWhite)

            // High-carbon stainless steel runner
            canvas.drawLine(-0.64 * r, 0.12 * r, 0.64 * r, 0.12 * r, runnerSteel)
            canvas.drawCircle(0.48 * r, 0.12 * r, 0.035 * r, runnerGlint)

            // Contoured boot with tendon guard
            tmpRect = CGRect(left: -0.5 * r, top: -0.21 * r, right: 0.5 * r, bottom: 0.21 * r)
            canvas.drawRoundRect(tmpRect, 0.15 * r, 0.15 * r, bootPaint)

            // Laces
            bootLacePaint.strokeWidth = 0.05 * r
            canvas.drawLine(-0.24 * r, -0.06 * r, 0.28 * r, -0.06 * r, bootLacePaint)
            canvas.drawLine(-0.24 * r, 0.06 * r, 0.28 * r, 0.06 * r, bootLacePaint)
            canvas.restore()
        }

        // Hockey Pants (Breezers) - dual thigh shells & kidney belt
        tmpRect = CGRect(left: -0.85 * r, top: -0.85 * r, right: -0.22 * r, bottom: 0.85 * r)
        canvas.drawRoundRect(tmpRect, 0.22 * r, 0.22 * r, pantsPaint)
        canvas.drawRoundRect(tmpRect, 0.22 * r, 0.22 * r, pantsOutline)
        for side: CGFloat in [-1, 1] {
            val_side_pants: do {
                let py = side * 0.48 * r
                tmpRect = CGRect(left: -0.96 * r, top: py - 0.36 * r, right: -0.26 * r, bottom: py + 0.36 * r)
                canvas.drawRoundRect(tmpRect, 0.16 * r, 0.16 * r, pantsPaint)
                canvas.drawRoundRect(tmpRect, 0.16 * r, 0.16 * r, pantsOutline)
                // Accent stripe on outer leg shell
                pantsStripe.color = info.primary
                pantsStripe.strokeWidth = 0.11 * r
                canvas.drawLine(-0.92 * r, py + side * 0.25 * r, -0.32 * r, py + side * 0.25 * r, pantsStripe)
            }
        }

        // Stick and arms: swings on shot/pass, flexes shaft on shot wind-up
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

        // Arms with sleeve stripes
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

        // Shaft: bows dynamically under shot tension (stick flex)
        shaftDark.strokeWidth = 0.2 * r
        shaftCore.strokeWidth = 0.08 * r
        if charge > 0.05 {
            let flexDisp = charge * 0.65 * r
            let midX = (bX + hX) * 0.5 - dY * flexDisp
            let midY = (bY + hY) * 0.5 + dX * flexDisp
            tmpPath.reset()
            tmpPath.moveTo(bX, bY)
            tmpPath.quadTo(midX, midY, hX, hY)
            canvas.drawPath(tmpPath, shaftDark)
            canvas.drawPath(tmpPath, shaftCore)
        } else {
            canvas.drawLine(bX, bY, hX, hY, shaftDark)
            canvas.drawLine(bX, bY, hX, hY, shaftCore)
        }
        tapePaint.strokeWidth = 0.22 * r
        canvas.drawLine(bX, bY, bX + dX * 0.4 * r, bY + dY * 0.4 * r, tapePaint)

        // Curved blade with serrated tape wraps and puck scuff
        tmpPath.reset()
        tmpPath.moveTo(hX, hY)
        tmpPath.quadTo(bladeLocal + 0.05, 0.42, bladeLocal + 0.5, -0.25)
        bladeOutline.strokeWidth = 0.46
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.3
        canvas.drawPath(tmpPath, bladeTape)

        // Tape wrap ribs along blade
        tapePaint.strokeWidth = 0.07 * r
        for ti in 0...3 {
            let frac: CGFloat = 0.25 + CGFloat(ti) * 0.18
            let tx = hX + (bladeLocal + 0.5 - hX) * frac
            let ty = hY + (-0.25 - hY) * frac
            canvas.drawLine(tx - 0.08 * r, ty - 0.12 * r, tx + 0.08 * r, ty + 0.12 * r, tapePaint)
        }
        canvas.drawCircle(bladeLocal + 0.1, 0.22, 0.12 * r, puckScuffPaint)

        // Segmented gloves on the shaft
        drawGlove(canvas, tX, tY, 0.4 * r, s.team, info)
        drawGlove(canvas, uX, uY, 0.4 * r, s.team, info)
        canvas.restore()

        // Torso with shaded jersey, shoulder pads, and yoke stripes
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

        // Pro sculpted helmet with vents, ear guards, visor gleam, and chin strap
        helmetPaint.color = helmetColorFor(s.team)
        let hx = 0.28 * r
        let hr = 0.58 * r

        // Ear protectors
        canvas.drawCircle(hx - 0.08 * r, -0.52 * r, 0.14 * r, earGuardPaint)
        canvas.drawCircle(hx - 0.08 * r, 0.52 * r, 0.14 * r, earGuardPaint)

        // Helmet shell
        canvas.drawCircle(hx, 0, hr, helmetPaint)
        canvas.drawCircle(hx, 0, hr, helmetOutline)

        // Aerodynamic ventilation ports on crown
        for vy: CGFloat in [-0.25, 0.25] {
            tmpRect = CGRect(left: hx - 0.3 * r, top: vy * r - 0.06 * r, right: hx - 0.05 * r, bottom: vy * r + 0.06 * r)
            canvas.drawRoundRect(tmpRect, 0.05 * r, 0.05 * r, helmetVent)
        }

        // Chin strap
        canvas.drawLine(hx - 0.1 * r, -0.45 * r, hx + 0.35 * r, 0, chinStrapPaint)
        canvas.drawLine(hx - 0.1 * r, 0.45 * r, hx + 0.35 * r, 0, chinStrapPaint)

        // Tinted curved Oakley-style visor with dual-specular gloss highlight
        tmpRect = CGRect(left: hx - hr, top: -hr, right: hx + hr, bottom: hr)
        canvas.drawArc(tmpRect, -50, 100, false, visorPaint)
        canvas.drawArc(tmpRect, -45, 90, false, visorGleamPaint)
        canvas.drawCircle(hx + 0.15 * r, -0.2 * r, 0.15 * r, glossPaint)
    }

    private func drawGoalieBody(_ canvas: GCanvas, _ g: Skater, _ info: TeamInfo, _ shader: RadialGradientSpec) {
        let r = CGFloat(g.radius)
        canvas.scale(Renderer.GOALIE_SCALE, Renderer.GOALIE_SCALE)
        let bladeLocal = CGFloat(g.stickReach) / Renderer.GOALIE_SCALE
        padStripe.color = info.primary

        if g.goalieAction == .padStack {
            canvas.rotate(CGFloat(g.padStackDir) * 65)
            for pIdx in 0...1 {
                let pY: CGFloat = pIdx == 0 ? -1.0 * r : -0.1 * r
                tmpRect = CGRect(left: -0.95 * r, top: pY - 0.42 * r, right: 0.95 * r, bottom: pY + 0.42 * r)
                canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padOutline)
                tmpRect = CGRect(left: -0.35 * r, top: pY - 0.28 * r, right: 0.35 * r, bottom: pY + 0.28 * r)
                canvas.drawRect(tmpRect, padStripe)
                for k: CGFloat in [-1, 0, 1] {
                    let kx = k * 0.45 * r
                    canvas.drawLine(kx, pY - 0.38 * r, kx, pY + 0.38 * r, padCrease)
                }
            }
            shaftDark.strokeWidth = 0.38 * r
            canvas.drawLine(-0.4 * r, -1.5 * r, bladeLocal + 0.3, -1.5 * r, shaftDark)
            shaftCore.strokeWidth = 0.16 * r
            canvas.drawLine(-0.4 * r, -1.5 * r, bladeLocal + 0.3, -1.5 * r, shaftCore)
            tmpPath.reset()
            tmpPath.moveTo(bladeLocal - 0.7, -1.5 * r)
            tmpPath.lineTo(bladeLocal + 0.5, -1.5 * r)
            bladeOutline.strokeWidth = 0.55
            canvas.drawPath(tmpPath, bladeOutline)
            bladeTape.strokeWidth = 0.38
            canvas.drawPath(tmpPath, bladeTape)

            torsoPaint.shader = shader
            tmpRect = CGRect(left: -0.85 * r, top: 0.4 * r, right: 0.75 * r, bottom: 1.8 * r)
            canvas.drawOval(tmpRect, torsoPaint)
            canvas.drawOval(tmpRect, bodyOutline)

            canvas.drawCircle(0.85 * r, 1.7 * r, 0.48 * r, leatherPaint)
            canvas.drawCircle(0.85 * r, 1.7 * r, 0.48 * r, gloveOutline)
            canvas.drawCircle(0.2 * r, 1.7 * r, 0.45 * r, glovePaint)

            maskPaint.color = info.secondary
            let mx = 0.25 * r
            let mr = 0.58 * r
            canvas.drawCircle(mx, 1.7 * r, mr, maskPaint)
            canvas.drawCircle(mx, 1.7 * r, mr, helmetOutline)
            return
        }

        // Leg pads: flared butterfly with sliding plates or upright 3-roll stance
        if g.goalieAction == .butterfly || g.butterfly {
            canvas.save()
            canvas.translate(-0.1 * r, -0.4 * r)
            canvas.rotate(-38)
            // Left butterfly pad
            tmpRect = CGRect(left: -0.7 * r, top: -1.35 * r, right: 0.7 * r, bottom: 0.15 * r)
            canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padOutline)
            tmpRect = CGRect(left: -0.6 * r, top: -0.1 * r, right: 0.6 * r, bottom: 0.12 * r)
            canvas.drawRoundRect(tmpRect, 0.08 * r, 0.08 * r, padPlate)
            tmpRect = CGRect(left: -0.25 * r, top: -1.2 * r, right: 0.15 * r, bottom: 0.05 * r)
            canvas.drawRect(tmpRect, padStripe)
            for k: CGFloat in [-1, 0, 1] {
                let kx = k * 0.32 * r
                canvas.drawLine(kx, -1.15 * r, kx, -0.1 * r, padCrease)
            }
            canvas.restore()

            canvas.save()
            canvas.translate(-0.1 * r, 0.4 * r)
            canvas.rotate(38)
            // Right butterfly pad
            tmpRect = CGRect(left: -0.7 * r, top: -0.15 * r, right: 0.7 * r, bottom: 1.35 * r)
            canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.28 * r, 0.28 * r, padOutline)
            tmpRect = CGRect(left: -0.6 * r, top: -0.12 * r, right: 0.6 * r, bottom: 0.1 * r)
            canvas.drawRoundRect(tmpRect, 0.08 * r, 0.08 * r, padPlate)
            tmpRect = CGRect(left: -0.25 * r, top: -0.05 * r, right: 0.15 * r, bottom: 1.2 * r)
            canvas.drawRect(tmpRect, padStripe)
            for k: CGFloat in [-1, 0, 1] {
                let kx = k * 0.32 * r
                canvas.drawLine(kx, 0.1 * r, kx, 1.15 * r, padCrease)
            }
            canvas.restore()

            // Five-hole sealed knee stack blocks
            tmpRect = CGRect(left: -0.45 * r, top: -0.38 * r, right: 0.35 * r, bottom: 0.38 * r)
            canvas.drawRoundRect(tmpRect, 0.18 * r, 0.18 * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.18 * r, 0.18 * r, padOutline)
        } else {
            // Upright ready stance pads with 3-tier knee rolls
            for side: CGFloat in [-1, 1] {
                let inner = side * 0.32 * r
                let outer = side * 1.12 * r
                tmpRect = CGRect(left: -0.7 * r, top: min(inner, outer), right: 0.72 * r, bottom: max(inner, outer))
                canvas.drawRoundRect(tmpRect, 0.3 * r, 0.3 * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.3 * r, 0.3 * r, padOutline)
                tmpRect = CGRect(left: -0.2 * r, top: min(inner, outer) + 0.08 * r, right: 0.08 * r, bottom: max(inner, outer) - 0.08 * r)
                canvas.drawRect(tmpRect, padStripe)
                for k: CGFloat in [-1, 0, 1] {
                    let kx = k * 0.32 * r
                    canvas.drawLine(kx, min(inner, outer) + 0.1 * r, kx, max(inner, outer) - 0.1 * r, padCrease)
                }
            }
        }

        // Goalie stick: wide reinforced paddle tapering down to wide curved blade
        let pokeDist: CGFloat = g.pokeTimer > 0 ? 1.6 : 0
        shaftDark.strokeWidth = 0.44 * r
        canvas.drawLine(0.45 * r + pokeDist, 1.0 * r, bladeLocal - 0.9 + pokeDist, 0.85, shaftDark)
        shaftCore.strokeWidth = 0.18 * r
        canvas.drawLine(0.45 * r + pokeDist, 1.0 * r, bladeLocal - 0.9 + pokeDist, 0.85, shaftCore)
        tmpPath.reset()
        tmpPath.moveTo(bladeLocal - 0.95 + pokeDist, 0.9)
        tmpPath.quadTo(bladeLocal + pokeDist, 0.7, bladeLocal + 0.55 + pokeDist, 0.15)
        bladeOutline.strokeWidth = 0.58
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.40
        canvas.drawPath(tmpPath, bladeTape)

        // Arms & shoulder floaters
        armOutline.strokeWidth = 0.68 * r
        canvas.drawLine(-0.1 * r, -0.9 * r, 0.8 * r, -1.15 * r, armOutline)
        canvas.drawLine(-0.1 * r, 0.9 * r, 0.75 * r, 1.05 * r, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.55 * r
        canvas.drawLine(-0.1 * r, -0.9 * r, 0.8 * r, -1.15 * r, armPaint)
        canvas.drawLine(-0.1 * r, 0.9 * r, 0.75 * r, 1.05 * r, armPaint)

        // Chest protector under the jersey
        torsoPaint.shader = shader
        tmpRect = CGRect(left: -1.0 * r, top: -1.2 * r, right: 0.85 * r, bottom: 1.2 * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.2 * r, -1.05 * r, 0.4 * r, torsoPaint)
        canvas.drawCircle(-0.2 * r, 1.05 * r, 0.4 * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.32 * r
        canvas.drawLine(-0.5 * r, -1.0 * r, -0.5 * r, 1.0 * r, yokePaint)

        // Catching glove (trapper): laced T-trap pocket webbing
        canvas.drawCircle(0.95 * r, -1.25 * r, 0.52 * r, leatherPaint)
        canvas.drawCircle(0.95 * r, -1.25 * r, 0.52 * r, gloveOutline)
        canvas.drawCircle(1.02 * r, -1.3 * r, 0.3 * r, leatherLight)
        canvas.drawLine(0.72 * r, -1.25 * r, 1.22 * r, -1.25 * r, trapperLace)
        canvas.drawLine(0.95 * r, -1.48 * r, 0.95 * r, -1.02 * r, trapperLace)

        // Blocker: beveled rectangular deflection board with angled face
        canvas.save()
        canvas.translate(0.95 * r, 1.15 * r)
        canvas.rotate(-15)
        tmpRect = CGRect(left: -0.38 * r, top: -0.52 * r, right: 0.38 * r, bottom: 0.52 * r)
        canvas.drawRoundRect(tmpRect, 0.12 * r, 0.12 * r, padPaint)
        canvas.drawRoundRect(tmpRect, 0.12 * r, 0.12 * r, gloveOutline)
        tmpRect = CGRect(left: -0.32 * r, top: -0.46 * r, right: 0.32 * r, bottom: 0.46 * r)
        canvas.drawRoundRect(tmpRect, 0.08 * r, 0.08 * r, blockerBevel)
        tmpRect = CGRect(left: -0.36 * r, top: -0.1 * r, right: 0.36 * r, bottom: 0.1 * r)
        canvas.drawRect(tmpRect, padStripe)
        canvas.restore()

        // Mask: sculpted fiberglass shell with chrome cat-eye cage
        maskPaint.color = info.secondary
        let mx = 0.32 * r
        let mr = 0.62 * r
        canvas.drawCircle(mx, 0, mr, maskPaint)
        canvas.drawCircle(mx, 0, mr, helmetOutline)

        for yy: CGFloat in [-0.28, 0, 0.28] {
            canvas.drawLine(mx + 0.15 * r, yy * r, mx + 0.58 * r, yy * r * 0.8, catEyeCage)
        }
        canvas.drawLine(mx + 0.26 * r, -0.38 * r, mx + 0.26 * r, 0.38 * r, catEyeCage)
        canvas.drawLine(mx + 0.44 * r, -0.32 * r, mx + 0.44 * r, 0.32 * r, catEyeCage)
        tmpRect = CGRect(left: mx + 0.12 * r, top: -0.22 * r, right: mx + 0.52 * r, bottom: 0.22 * r)
        canvas.drawArc(tmpRect, -70, 140, false, catEyeCage)
        canvas.drawCircle(mx + 0.05 * r, -0.28 * r, 0.13 * r, glossPaint)
    }

    func drawPuck(_ canvas: GCanvas, _ p: Puck) {
        let sp = CGFloat(p.speed)
        let x = CGFloat(p.x), y = CGFloat(p.y)
        puckTrailX[puckTrailHead] = x
        puckTrailY[puckTrailHead] = y
        puckTrailHead = (puckTrailHead + 1) % 8

        if p.carrier == nil && sp > 35 {
            let isBoomer = sp > 95
            for i in 1..<8 {
                let currIdx = (puckTrailHead - i + 8) % 8
                let prevIdx = (puckTrailHead - i - 1 + 8) % 8
                let frac = 1.0 - (CGFloat(i) / 8.0)
                let alpha = Int(frac * 190)
                if isBoomer {
                    cometTrail.color = HexColor.argb(alpha, 255, Int(120 * frac + 30), 20)
                    cometTrail.strokeWidth = 1.0 * frac + 0.3
                } else {
                    cometTrail.color = HexColor.argb(alpha, 56, 189, 248)
                    cometTrail.strokeWidth = 0.65 * frac + 0.2
                }
                canvas.drawLine(puckTrailX[prevIdx], puckTrailY[prevIdx], puckTrailX[currIdx], puckTrailY[currIdx], cometTrail)
            }
            if isBoomer {
                cometGlow.color = HexColor.argb(130, 255, 140, 20)
                canvas.drawCircle(x, y, 2.0, cometGlow)
            }
        }

        // Dual-tier shadow
        canvas.drawCircle(x + 0.22, y + 0.28, 0.95, shadowPaint)
        canvas.drawCircle(x + 0.08, y + 0.1, 0.82, contactShadow)

        // Vulcanized rubber puck body with knurled textured edge
        canvas.drawCircle(x, y, 0.95, puckHalo)
        canvas.drawCircle(x, y, 0.82, puckPaint)
        canvas.drawCircle(x, y, 0.80, puckKnurl)

        // Beveled upper rim & center embossed medallion
        canvas.drawCircle(x, y, 0.62, puckBevel)
        canvas.drawCircle(x, y, 0.38, puckRim)
        canvas.drawCircle(x - 0.12, y - 0.12, 0.18, glossPaint)
    }
}
