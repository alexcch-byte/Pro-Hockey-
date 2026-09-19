import CoreGraphics
import Foundation

/// Draws the world (rink, players, puck) through the Camera, then the
/// scoreboard, banners and touch controls in screen space.
///
/// Port of Renderer.kt over the GCanvas/GPaint/GPath shim in this folder
/// (see GraphicsCompat.swift, GCanvas.swift) instead of android.graphics
/// directly. This is the single largest, most visual, and least verifiable
/// file in the whole port -- there was no way to render a frame and look at
/// it while writing this. Two spots carry real, specific risk and are
/// flagged where they occur: drawArc's angle convention (mitigated by
/// building arcs from the same cos/sin math the rest of the engine already
/// uses, rather than trusting CGContext's own "clockwise" flag under a
/// flipped CTM) and the crowd bitmap's coordinate flip (buildCrowd flips its
/// offscreen context to y-down at creation; GCanvas.drawImage flips back for
/// the single compositing call). Check those first.
final class Renderer {
    /// Bodies are drawn larger than their physics radius so they read well on 8-inch screens.
    static let BODY_SCALE: CGFloat = 1.3
    static let GOALIE_SCALE: CGFloat = 1.2
    private static let MAX_SPRAY = 240

    // Float(Rink/Camera) constants mirrored as CGFloat so the drawing code
    // below doesn't need a CGFloat(...) wrapper at every use.
    private static let rinkHalfL = CGFloat(Rink.HALF_L)
    private static let rinkHalfW = CGFloat(Rink.HALF_W)
    private static let rinkCornerR = CGFloat(Rink.CORNER_R)
    private static let goalLineX = CGFloat(Rink.GOAL_LINE_X)
    private static let blueLineX = CGFloat(Rink.BLUE_LINE_X)
    private static let goalHalfW = CGFloat(Rink.GOAL_HALF_W)
    private static let netDepth = CGFloat(Rink.NET_DEPTH)
    private static let netHalfW = CGFloat(Rink.NET_HALF_W)
    private static let creaseR = CGFloat(Rink.CREASE_R)
    private static let faceoffR = CGFloat(Rink.FACEOFF_R)
    private static let endDotX = CGFloat(Rink.END_DOT_X)
    private static let neutralDotX = CGFloat(Rink.NEUTRAL_DOT_X)
    private static let dotY = CGFloat(Rink.DOT_Y)
    private static let postR = CGFloat(Rink.POST_R)
    private static let worldHalfW = CGFloat(Camera.WORLD_HALF_W)
    private static let worldHalfH = CGFloat(Camera.WORLD_HALF_H)

    let camera = Camera()
    let density: CGFloat

    let rinkRect: CGRect
    private let rinkPath: GPath
    var tmpRect: CGRect = .zero
    let tmpPath = GPath()

    private var crowd: CGImage?
    private let crowdRect: CGRect
    var animTime: CGFloat = 0

    // ----- paints (world space, stroke widths in feet)
    private let icePaint = GPaint(color: HexColor.argb("#EDF4F9"))
    private let iceShadePaint = GPaint(color: HexColor.argb("#DCE8F1"))
    let redLine = GPaint(color: HexColor.argb("#D7263D"), style: .stroke, strokeWidth: 0.6)
    let blueLine = GPaint(color: HexColor.argb("#1F5FBF"), style: .stroke, strokeWidth: 1)
    let centerLine = GPaint(color: HexColor.argb("#D7263D"), style: .stroke, strokeWidth: 1)
    let circleRed = GPaint(color: HexColor.argb("#D7263D"), style: .stroke, strokeWidth: 0.28)
    let circleBlue = GPaint(color: HexColor.argb("#1F5FBF"), style: .stroke, strokeWidth: 0.28)
    let dotRed = GPaint(color: HexColor.argb("#D7263D"))
    let dotBlue = GPaint(color: HexColor.argb("#1F5FBF"))
    let creaseFill = GPaint(color: HexColor.argb("#BFE0F5"))
    let trapezoid = GPaint(color: HexColor.argb("#D7263D"), style: .stroke, strokeWidth: 0.2)
    let netFill = GPaint(color: HexColor.argb("#D9DEE3"))
    let netMesh = GPaint(color: HexColor.argb("#9AA3AD"), style: .stroke, strokeWidth: 0.08)
    let netFrame = GPaint(color: HexColor.argb("#D7263D"), style: .stroke, strokeWidth: 0.35, strokeCap: .round)
    let postPaint = GPaint(color: HexColor.argb("#E23B4F"))
    let kickPlate = GPaint(color: HexColor.argb("#F4C542"), style: .stroke, strokeWidth: 0.9)
    let boardsPaint = GPaint(color: HexColor.argb("#F8FAFC"), style: .stroke, strokeWidth: 2.2)
    let glassPaint = GPaint(color: HexColor.argb(190, 0x7D, 0xA6, 0xC9), style: .stroke, strokeWidth: 0.7)
    let logoPaint = GPaint()
    let logoText = GPaint(textAlign: .center, textSize: 7, bold: true)
    let faceoffPulse = GPaint(color: HexColor.argb("#FBBF24"), style: .stroke, strokeWidth: 0.4)

    // ----- player paints
    let shadowPaint = GPaint(color: HexColor.argb(70, 10, 20, 40))
    let torsoPaint = GPaint()
    let bodyOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeWidth: 0.16)
    let yokePaint = GPaint(style: .stroke, strokeCap: .round)
    let yokeThin = GPaint(color: HexColor.argb("#F8FAFC"), style: .stroke, strokeCap: .round)
    let armPaint = GPaint(style: .stroke, strokeCap: .round)
    let armOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeCap: .round)
    let sleevePaint = GPaint(style: .stroke, strokeCap: .butt)
    let helmetPaint = GPaint()
    let helmetOutline = GPaint(color: HexColor.argb("#0B1220"), style: .stroke, strokeWidth: 0.12)
    let visorPaint = GPaint(color: HexColor.argb(150, 125, 211, 252))
    let glossPaint = GPaint(color: HexColor.argb(120, 255, 255, 255))
    let glovePaint = GPaint()
    let gloveCuff = GPaint()
    let gloveOutline = GPaint(color: HexColor.argb("#0B1220"), style: .stroke, strokeWidth: 0.09)
    let bootPaint = GPaint(color: HexColor.argb("#111827"))
    let bootLacePaint = GPaint(color: HexColor.argb("#CBD5E1"), style: .stroke, strokeCap: .round)
    let skateBladePaint = GPaint(color: HexColor.argb("#B8C6D4"), style: .stroke, strokeCap: .round)
    let shaftDark = GPaint(color: HexColor.argb("#2B1D12"), style: .stroke, strokeCap: .round)
    let shaftCore = GPaint(color: HexColor.argb("#A0642F"), style: .stroke, strokeCap: .round)
    let tapePaint = GPaint(color: HexColor.argb("#F2F2F2"), style: .stroke, strokeCap: .round)
    let bladeOutline = GPaint(color: HexColor.argb("#111111"), style: .stroke, strokeCap: .round, strokeJoin: .round)
    let bladeTape = GPaint(color: HexColor.argb("#E8E8E8"), style: .stroke, strokeCap: .round, strokeJoin: .round)
    let padPaint = GPaint(color: HexColor.argb("#F3F4F6"))
    let padOutline = GPaint(color: HexColor.argb("#1F2937"), style: .stroke, strokeWidth: 0.12)
    let padStripe = GPaint()
    let leatherPaint = GPaint(color: HexColor.argb("#8B5A2B"))
    let leatherLight = GPaint(color: HexColor.argb("#C58B4A"))
    let maskPaint = GPaint()
    let cagePaint = GPaint(color: HexColor.argb("#111827"), style: .stroke, strokeWidth: 0.06)
    let starPaint = GPaint(color: HexColor.argb("#FDE047"), style: .stroke, strokeWidth: 0.14, strokeCap: .round)
    let sprayOuter = GPaint(color: HexColor.argb("#8FB3CC"))
    let sprayInner = GPaint(color: HexColor.white)
    let numberPaint = GPaint(textAlign: .center, textSize: 1.7, bold: true)
    let ringPaint = GPaint(color: HexColor.argb("#FDE047"), style: .stroke, strokeWidth: 0.3)
    let ringGlow = GPaint(color: HexColor.argb(60, 253, 224, 71))
    let puckPaint = GPaint(color: HexColor.argb("#0A0A0A"))
    let puckRim = GPaint(color: HexColor.argb("#3A3A3A"), style: .stroke, strokeWidth: 0.12)
    let puckHalo = GPaint(color: HexColor.argb(200, 255, 255, 255))
    let puckTrail = GPaint(color: HexColor.argb(90, 20, 20, 20), style: .stroke, strokeWidth: 0.6, strokeCap: .round)
    let meterBack = GPaint(color: HexColor.argb(140, 0, 0, 0), style: .stroke, strokeWidth: 0.5)
    let meterFill = GPaint(color: HexColor.argb("#F97316"), style: .stroke, strokeWidth: 0.5, strokeCap: .round)

    // ----- HUD paints (screen space)
    let hudBack = GPaint(color: HexColor.argb(245, 8, 14, 26))
    let hudText = GPaint(color: HexColor.white, textAlign: .center, bold: true)
    let hudSmall = GPaint(color: HexColor.argb("#CBD5E1"), textAlign: .center)
    let hudTeamBox = GPaint()
    let bannerBack = GPaint(color: HexColor.argb(170, 5, 10, 20))
    let bannerText = GPaint(color: HexColor.white, textAlign: .center, bold: true)
    let bannerSub = GPaint(color: HexColor.argb("#FDE68A"), textAlign: .center, bold: true)
    let ctrlBase = GPaint(color: HexColor.argb(70, 255, 255, 255))
    let ctrlRing = GPaint(color: HexColor.argb(140, 255, 255, 255), style: .stroke)
    let ctrlKnob = GPaint(color: HexColor.argb(200, 255, 255, 255))
    let btnFill = GPaint()
    let btnText = GPaint(color: HexColor.white, textAlign: .center, bold: true)
    let chargeArc = GPaint(color: HexColor.argb("#FDE047"), style: .stroke, strokeCap: .round)

    // ----- per-team cached jersey shading
    private var shaderColor: [UInt32] = [0, 0]
    private var skaterShader: [RadialGradientSpec?] = [nil, nil]
    private var goalieShader: [RadialGradientSpec?] = [nil, nil]
    private var helmetColor: [UInt32] = [0, 0]
    private var gloveColor: [UInt32] = [0, 0]

    // ----- snow spray particles + per-skater motion memory
    private var sprayX = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayY = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayVx = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayVy = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayLife = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayMax = [CGFloat](repeating: 0, count: Renderer.MAX_SPRAY)
    private var sprayHead = 0
    private let sprayRng = SeededRandom(seed: 11)
    private var emaVx = [CGFloat](repeating: 0, count: 12)
    private var emaVy = [CGFloat](repeating: 0, count: 12)
    private var sprayCooldown = [CGFloat](repeating: 0, count: 12)
    private var wasStunned = [Bool](repeating: false, count: 12)

    init(density: Float) {
        self.density = CGFloat(density)
        rinkRect = CGRect(left: -Renderer.rinkHalfL, top: -Renderer.rinkHalfW, right: Renderer.rinkHalfL, bottom: Renderer.rinkHalfW)
        let path = GPath()
        path.addRoundRect(rinkRect, Renderer.rinkCornerR, Renderer.rinkCornerR)
        rinkPath = path
        crowdRect = CGRect(left: -Renderer.worldHalfW, top: -Renderer.worldHalfH, right: Renderer.worldHalfW, bottom: Renderer.worldHalfH)
    }

    func resize(_ w: Int, _ h: Int) {
        camera.resize(w, h)
        buildCrowd()
    }

    private func buildCrowd() {
        let pxPerFt: CGFloat = 5
        let bw = max(8, Int(crowdRect.width * pxPerFt))
        let bh = max(8, Int(crowdRect.height * pxPerFt))
        guard let ctx = CGContext(data: nil, width: bw, height: bh, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return }
        // Raw bitmap contexts default to y-up, bottom-left origin; flip to
        // y-down here so the drawing below can use the same cos/sin-style
        // math as the rest of the engine. See GCanvas.drawImage for the
        // matching counter-flip when this image is composited back in.
        ctx.translateBy(x: 0, y: CGFloat(bh))
        ctx.scaleBy(x: 1, y: -1)
        let canvas = GCanvas(context: ctx, bounds: CGRect(x: 0, y: 0, width: bw, height: bh))
        canvas.drawColor(HexColor.argb("#0B1424"))
        let rng = SeededRandom(seed: 7)
        let p = GPaint()
        let palette: [UInt32] = [
            HexColor.argb("#1E293B"), HexColor.argb("#334155"), HexColor.argb("#7F1D1D"), HexColor.argb("#1E3A8A"),
            HexColor.argb("#374151"), HexColor.argb("#4B5563"), HexColor.argb("#9A3412"), HexColor.argb("#14532D"),
            HexColor.argb("#F1F5F9"), HexColor.argb("#FDE68A")
        ]
        // Rows of seats radiating outwards from the boards.
        canvas.translate(CGFloat(bw) / 2, CGFloat(bh) / 2)
        canvas.scale(pxPerFt, pxPerFt)
        let stepRow: CGFloat = 1.6
        let stepSeat: CGFloat = 1.15
        var ring = Renderer.rinkHalfW + 3.5
        var row = 0
        while ring < Renderer.worldHalfH + 1 {
            let halfL = Renderer.rinkHalfL + 3.5 + CGFloat(row) * stepRow
            let halfW = ring
            let shade = 1 - CGFloat(row) * 0.07
            var sx = -halfL
            while sx <= halfL {
                for sy: CGFloat in [-halfW, halfW] {
                    p.color = shadeColor(palette[rng.nextInt(palette.count)], shade)
                    canvas.drawCircle(sx + CGFloat(rng.nextFloat()) * 0.3, sy + CGFloat(rng.nextFloat()) * 0.3, 0.48, p)
                }
                sx += stepSeat
            }
            var sy = -halfW
            while sy <= halfW {
                for sx2: CGFloat in [-halfL, halfL] {
                    p.color = shadeColor(palette[rng.nextInt(palette.count)], shade)
                    canvas.drawCircle(sx2 + CGFloat(rng.nextFloat()) * 0.3, sy + CGFloat(rng.nextFloat()) * 0.3, 0.48, p)
                }
                sy += stepSeat
            }
            ring += stepRow
            row += 1
        }
        // Dark walkway right behind the glass.
        p.color = HexColor.argb("#0F1B2E")
        p.style = .stroke
        p.strokeWidth = 3
        canvas.drawRoundRect(CGRect(left: -Renderer.rinkHalfL - 2.5, top: -Renderer.rinkHalfW - 2.5, right: Renderer.rinkHalfL + 2.5, bottom: Renderer.rinkHalfW + 2.5), Renderer.rinkCornerR + 2.5, Renderer.rinkCornerR + 2.5, p)
        crowd = ctx.makeImage()
    }

    private func shadeColor(_ color: UInt32, _ f: CGFloat) -> UInt32 {
        let r = min(max(Int(CGFloat(HexColor.red(color)) * f), 0), 255)
        let g = min(max(Int(CGFloat(HexColor.green(color)) * f), 0), 255)
        let b = min(max(Int(CGFloat(HexColor.blue(color)) * f), 0), 255)
        return HexColor.rgb(r, g, b)
    }

    private func lighten(_ color: UInt32, _ f: CGFloat) -> UInt32 {
        let r = min(max(Int(CGFloat(HexColor.red(color)) + CGFloat(255 - HexColor.red(color)) * f), 0), 255)
        let g = min(max(Int(CGFloat(HexColor.green(color)) + CGFloat(255 - HexColor.green(color)) * f), 0), 255)
        let b = min(max(Int(CGFloat(HexColor.blue(color)) + CGFloat(255 - HexColor.blue(color)) * f), 0), 255)
        return HexColor.rgb(r, g, b)
    }

    private func ensureTeamShaders(_ world: World) {
        for t in 0...1 {
            let primary = world.teams[t].info.primary
            if shaderColor[t] == primary && skaterShader[t] != nil { continue }
            shaderColor[t] = primary
            let colors: [UInt32] = [lighten(primary, 0.42), primary, shadeColor(primary, 0.68)]
            let stops: [CGFloat] = [0, 0.55, 1]
            skaterShader[t] = RadialGradientSpec(colors: colors, stops: stops, centerX: -0.2, centerY: -0.25, radius: 1.5 * 1.15)
            goalieShader[t] = RadialGradientSpec(colors: colors, stops: stops, centerX: -0.3, centerY: -0.3, radius: 2.1 * 1.25)
            helmetColor[t] = shadeColor(primary, 0.55)
            gloveColor[t] = shadeColor(primary, 0.45)
        }
    }

    func skaterShaderFor(_ team: Int) -> RadialGradientSpec { skaterShader[team]! }
    func goalieShaderFor(_ team: Int) -> RadialGradientSpec { goalieShader[team]! }
    func helmetColorFor(_ team: Int) -> UInt32 { helmetColor[team] }
    func gloveColorFor(_ team: Int) -> UInt32 { gloveColor[team] }

    // ================================================================ frame

    func draw(_ canvas: GCanvas, _ world: World, _ localTeam: Int, _ controls: TouchControlsState?, _ dt: Float) {
        let dtf = CGFloat(dt)
        animTime += dtf
        ensureTeamShaders(world)
        updateSpray(world, dtf)

        canvas.drawColor(HexColor.argb("#0B1424"))
        canvas.save()
        camera.apply(canvas)
        if let crowd = crowd { canvas.drawImage(crowd, in: crowdRect) }
        drawRink(canvas, world)
        if world.phase == .faceoff {
            let r: CGFloat = 2.2 + 0.6 * sin(animTime * 8)
            faceoffPulse.alpha = 200
            canvas.drawCircle(CGFloat(world.faceoffX), CGFloat(world.faceoffY), r, faceoffPulse)
        }
        for s in world.allSkaters { drawShadow(canvas, s) }
        drawSpray(canvas)
        let sorted = world.allSkaters.sorted { $0.y < $1.y }
        let controlled: Skater? = localTeam >= 0 ? world.controlledSkater(localTeam) : nil
        for s in sorted {
            drawSkater(canvas, world, s, s === controlled, localTeam >= 0 ? CGFloat(world.shotCharge[localTeam]) : 0)
        }
        drawPuck(canvas, world.puck)
        canvas.restore()

        drawScoreboard(canvas, world)
        drawBanner(canvas, world)
        if let controls = controls { drawControls(canvas, world, localTeam, controls) }
        drawPauseButton(canvas)
    }

    // ================================================================ rink

    private func drawRink(_ canvas: GCanvas, _ world: World) {
        canvas.drawPath(rinkPath, boardsPaint)
        canvas.drawPath(rinkPath, icePaint)
        canvas.save()
        canvas.clipPath(rinkPath)
        // Faint zone shading toward the ends.
        tmpRect = CGRect(left: -Renderer.rinkHalfL, top: -Renderer.rinkHalfW, right: -Renderer.goalLineX, bottom: Renderer.rinkHalfW)
        canvas.drawRect(tmpRect, iceShadePaint)
        tmpRect = CGRect(left: Renderer.goalLineX, top: -Renderer.rinkHalfW, right: Renderer.rinkHalfL, bottom: Renderer.rinkHalfW)
        canvas.drawRect(tmpRect, iceShadePaint)

        // Goal lines, blue lines, centre line.
        canvas.drawLine(-Renderer.goalLineX, -Renderer.rinkHalfW, -Renderer.goalLineX, Renderer.rinkHalfW, redLine)
        canvas.drawLine(Renderer.goalLineX, -Renderer.rinkHalfW, Renderer.goalLineX, Renderer.rinkHalfW, redLine)
        canvas.drawLine(-Renderer.blueLineX, -Renderer.rinkHalfW, -Renderer.blueLineX, Renderer.rinkHalfW, blueLine)
        canvas.drawLine(Renderer.blueLineX, -Renderer.rinkHalfW, Renderer.blueLineX, Renderer.rinkHalfW, blueLine)
        canvas.drawLine(0, -Renderer.rinkHalfW, 0, Renderer.rinkHalfW, centerLine)

        // Centre ice logo + circle.
        logoPaint.color = world.teams[0].info.primary
        logoPaint.alpha = 60
        canvas.drawCircle(0, 0, 10, logoPaint)
        logoText.color = world.teams[0].info.primary
        logoText.alpha = 120
        canvas.drawText(world.teams[0].info.abbr, 0, 2.6, logoText)
        canvas.drawCircle(0, 0, Renderer.faceoffR, circleBlue)
        canvas.drawCircle(0, 0, 1, dotBlue)

        // Faceoff circles / dots.
        for sx: CGFloat in [-1, 1] {
            for sy: CGFloat in [-1, 1] {
                let cx = sx * Renderer.endDotX
                let cy = sy * Renderer.dotY
                canvas.drawCircle(cx, cy, Renderer.faceoffR, circleRed)
                canvas.drawCircle(cx, cy, 1, dotRed)
                // hash marks
                canvas.drawLine(cx - 3, cy - Renderer.faceoffR - 2, cx - 3, cy - Renderer.faceoffR, circleRed)
                canvas.drawLine(cx + 3, cy - Renderer.faceoffR - 2, cx + 3, cy - Renderer.faceoffR, circleRed)
                canvas.drawLine(cx - 3, cy + Renderer.faceoffR, cx - 3, cy + Renderer.faceoffR + 2, circleRed)
                canvas.drawLine(cx + 3, cy + Renderer.faceoffR, cx + 3, cy + Renderer.faceoffR + 2, circleRed)
                canvas.drawCircle(sx * Renderer.neutralDotX, cy, 1, dotRed)
            }
        }

        // Creases, trapezoids and nets at both ends.
        for e: CGFloat in [-1, 1] {
            let gx = e * Renderer.goalLineX
            tmpRect = CGRect(left: gx - Renderer.creaseR, top: -Renderer.creaseR, right: gx + Renderer.creaseR, bottom: Renderer.creaseR)
            let start: CGFloat = e > 0 ? 90 : -90
            canvas.drawArc(tmpRect, start, 180, true, creaseFill)
            canvas.drawArc(tmpRect, start, 180, false, circleRed)
            // trapezoid
            canvas.drawLine(gx, -11, e * Renderer.rinkHalfL, -14, trapezoid)
            canvas.drawLine(gx, 11, e * Renderer.rinkHalfL, 14, trapezoid)
            // net
            let backX = e * (Renderer.goalLineX + Renderer.netDepth)
            tmpRect = CGRect(left: min(gx, backX), top: -Renderer.netHalfW, right: max(gx, backX), bottom: Renderer.netHalfW)
            canvas.drawRect(tmpRect, netFill)
            var mx = tmpRect.minX
            while mx <= tmpRect.maxX { canvas.drawLine(mx, tmpRect.minY, mx, tmpRect.maxY, netMesh); mx += 0.6 }
            var my = tmpRect.minY
            while my <= tmpRect.maxY { canvas.drawLine(tmpRect.minX, my, tmpRect.maxX, my, netMesh); my += 0.6 }
            tmpPath.reset()
            tmpPath.moveTo(gx, -Renderer.netHalfW)
            tmpPath.lineTo(backX, -Renderer.netHalfW)
            tmpPath.lineTo(backX, Renderer.netHalfW)
            tmpPath.lineTo(gx, Renderer.netHalfW)
            canvas.drawPath(tmpPath, netFrame)
            canvas.drawCircle(gx, -Renderer.goalHalfW, Renderer.postR, postPaint)
            canvas.drawCircle(gx, Renderer.goalHalfW, Renderer.postR, postPaint)
        }
        canvas.restore()

        // Boards: kick plate inside, glass outside.
        canvas.drawPath(rinkPath, kickPlate)
        tmpRect = rinkRect.insetBy(dx: -1.1, dy: -1.1)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, Renderer.rinkCornerR + 1.1, Renderer.rinkCornerR + 1.1)
        canvas.drawPath(tmpPath, boardsPaint)
        tmpRect = tmpRect.insetBy(dx: -1.4, dy: -1.4)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, Renderer.rinkCornerR + 2.5, Renderer.rinkCornerR + 2.5)
        canvas.drawPath(tmpPath, glassPaint)
    }

    // ================================================================ spray

    private func updateSpray(_ world: World, _ dt: CGFloat) {
        for i in 0..<Renderer.MAX_SPRAY {
            if sprayLife[i] <= 0 { continue }
            sprayLife[i] -= dt
            sprayX[i] += sprayVx[i] * dt
            sprayY[i] += sprayVy[i] * dt
            let f = exp(-dt * 6)
            sprayVx[i] *= f
            sprayVy[i] *= f
        }
        let k = 1 - exp(-dt / 0.12)
        let skaters = world.allSkaters
        for i in skaters.indices {
            if i >= emaVx.count { break }
            let s = skaters[i]
            let dvx = CGFloat(s.vx) - emaVx[i]
            let dvy = CGFloat(s.vy) - emaVy[i]
            let dv = hypot(dvx, dvy)
            sprayCooldown[i] = max(0, sprayCooldown[i] - dt)
            let stunned = s.stunTimer > 0
            let knocked = stunned && !wasStunned[i]
            if (dv > 9 && sprayCooldown[i] <= 0) || knocked {
                // Snow flies along the old direction of travel, like a hockey stop.
                let dirX = dv > 0.01 ? -dvx / dv : cos(CGFloat(s.facing))
                let dirY = dv > 0.01 ? -dvy / dv : sin(CGFloat(s.facing))
                let px = -sin(CGFloat(s.facing)) * 0.9
                let py = cos(CGFloat(s.facing)) * 0.9
                let count = knocked ? 7 : 5
                let speed = 7 + min(dv, 30) * 0.35
                spawnSpray(CGFloat(s.x) + px, CGFloat(s.y) + py, dirX, dirY, count, speed)
                spawnSpray(CGFloat(s.x) - px, CGFloat(s.y) - py, dirX, dirY, count, speed)
                sprayCooldown[i] = 0.22
            }
            wasStunned[i] = stunned
            emaVx[i] += dvx * k
            emaVy[i] += dvy * k
        }
    }

    private func spawnSpray(_ x: CGFloat, _ y: CGFloat, _ dirX: CGFloat, _ dirY: CGFloat, _ count: Int, _ speed: CGFloat) {
        for _ in 0..<count {
            let i = sprayHead
            sprayHead = (sprayHead + 1) % Renderer.MAX_SPRAY
            let ang = (CGFloat(sprayRng.nextFloat()) - 0.5) * 2.1
            let c = cos(ang)
            let sn = sin(ang)
            let sp = speed * (0.45 + CGFloat(sprayRng.nextFloat()) * 0.8)
            sprayX[i] = x
            sprayY[i] = y
            sprayVx[i] = (dirX * c - dirY * sn) * sp
            sprayVy[i] = (dirX * sn + dirY * c) * sp
            sprayMax[i] = 0.28 + CGFloat(sprayRng.nextFloat()) * 0.22
            sprayLife[i] = sprayMax[i]
        }
    }

    private func drawSpray(_ canvas: GCanvas) {
        for i in 0..<Renderer.MAX_SPRAY {
            let life = sprayLife[i]
            if life <= 0 { continue }
            let t = min(max(life / sprayMax[i], 0), 1)
            let rad = 0.22 + (1 - t) * 0.28
            sprayOuter.alpha = Int(170 * t)
            sprayInner.alpha = Int(230 * t)
            canvas.drawCircle(sprayX[i], sprayY[i], rad, sprayOuter)
            canvas.drawCircle(sprayX[i], sprayY[i], rad * 0.55, sprayInner)
        }
    }

    func release() {
        crowd = nil
    }
}
