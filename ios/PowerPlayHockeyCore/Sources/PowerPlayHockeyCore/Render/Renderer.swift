import CoreGraphics
import Foundation

/// Draws the world (rink, players, puck) through the Camera, then the
/// scoreboard, banners and touch controls in screen space.
///
/// Port of Renderer.kt over GCanvas/GPaint/GPath.
final class Renderer {
    /// Bodies are drawn larger than their physics radius so they read well on mobile screens.
    static let BODY_SCALE: CGFloat = 1.3
    static let GOALIE_SCALE: CGFloat = 1.2
    private static let MAX_SPRAY = 240
    private static let MAX_SNOW = 110
    private static let MAX_BREATH = 80
    private static let MAX_SHARDS = 50
    private static let MAX_SCRATCHES = 160

    // Float(Rink/Camera) constants mirrored as CGFloat
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
    private var winterLandscape: CGImage?
    private let crowdRect: CGRect
    var animTime: CGFloat = 0

    // ----- Indoor rink paints
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

    // ----- Outdoor Winter Pond paints
    private let pondIcePaint = GPaint(color: HexColor.argb("#8FB9C9"))
    private let pondIceShadePaint = GPaint(color: HexColor.argb("#7CA6B7"))
    private let pondBoardsPaint = GPaint(color: HexColor.argb("#4A3319"), style: .stroke, strokeWidth: 2.2)
    private let pondKickPlate = GPaint(color: HexColor.argb("#6E4D27"), style: .stroke, strokeWidth: 0.9)
    private let pondSnowCapPaint = GPaint(color: HexColor.argb("#F0F8FF"), style: .stroke, strokeWidth: 1.6)
    private let pondCrackPaint = GPaint(color: HexColor.argb(160, 230, 245, 255), style: .stroke, strokeWidth: 0.14)

    // ----- Weather, FX & Particles
    private let sirenPaint = GPaint(color: HexColor.argb(200, 255, 30, 30))
    private let sirenBeam = GPaint(color: HexColor.argb(70, 255, 40, 40))
    private let snowflakePaint = GPaint(color: HexColor.argb(200, 255, 255, 255))
    private let breathPaint = GPaint(color: HexColor.argb(125, 235, 248, 255))
    private let glassSpiderwebPaint = GPaint(color: HexColor.argb(240, 220, 245, 255), style: .stroke, strokeWidth: 0.16)
    private let glassSpiderwebFill = GPaint(color: HexColor.argb(50, 180, 225, 255))
    private let glassShardFill = GPaint(color: HexColor.argb(190, 210, 240, 255))
    private let glassShardEdge = GPaint(color: HexColor.argb(250, 255, 255, 255), style: .stroke, strokeWidth: 0.08)
    private let scratchPaint = GPaint(color: HexColor.argb(35, 255, 255, 255), style: .stroke, strokeCap: .round)

    // ----- Player Sprite paints (Hockey Pants, TUUK Holders, 4-Roll Gloves, Oakley Visors, Stick Flex)
    let shadowPaint = GPaint(color: HexColor.argb(70, 10, 20, 40))
    let contactShadow = GPaint(color: HexColor.argb(110, 5, 10, 20))
    let torsoPaint = GPaint()
    let bodyOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeWidth: 0.16)
    let yokePaint = GPaint(style: .stroke, strokeCap: .round)
    let yokeThin = GPaint(color: HexColor.argb("#F8FAFC"), style: .stroke, strokeCap: .round)
    let armPaint = GPaint(style: .stroke, strokeCap: .round)
    let armOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeCap: .round)
    let sleevePaint = GPaint(style: .stroke, strokeCap: .butt)
    let helmetPaint = GPaint()
    let helmetOutline = GPaint(color: HexColor.argb("#0B1220"), style: .stroke, strokeWidth: 0.12)
    let helmetVent = GPaint(color: HexColor.argb("#050A14"))
    let earGuardPaint = GPaint(color: HexColor.argb("#E2E8F0"))
    let chinStrapPaint = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeWidth: 0.06)
    let visorPaint = GPaint(color: HexColor.argb(140, 14, 165, 233), style: .stroke, strokeWidth: 0.12)
    let visorGleamPaint = GPaint(color: HexColor.argb(220, 255, 255, 255), style: .stroke, strokeWidth: 0.07)
    let glossPaint = GPaint(color: HexColor.argb(130, 255, 255, 255))
    let glovePaint = GPaint()
    let gloveCuff = GPaint()
    let glovePalmPaint = GPaint(color: HexColor.argb("#D97706"))
    let gloveRollPaint = GPaint(color: HexColor.argb(180, 15, 23, 42), style: .stroke, strokeWidth: 0.07)
    let gloveOutline = GPaint(color: HexColor.argb("#0B1220"), style: .stroke, strokeWidth: 0.09)
    let pantsPaint = GPaint(color: HexColor.argb("#0F172A"))
    let pantsOutline = GPaint(color: HexColor.argb("#020617"), style: .stroke, strokeWidth: 0.09)
    let pantsStripe = GPaint(style: .stroke, strokeCap: .round)
    let bootPaint = GPaint(color: HexColor.argb("#0F172A"))
    let bootLacePaint = GPaint(color: HexColor.argb("#F8FAFC"), style: .stroke, strokeCap: .round)
    let holderWhite = GPaint(color: HexColor.argb("#F8FAFC"))
    let runnerSteel = GPaint(color: HexColor.argb("#E2E8F0"), style: .stroke, strokeWidth: 0.06, strokeCap: .round)
    let runnerGlint = GPaint(color: HexColor.argb("#FFFFFF"))
    let shaftDark = GPaint(color: HexColor.argb("#1E293B"), style: .stroke, strokeCap: .round)
    let shaftCore = GPaint(color: HexColor.argb("#0284C7"), style: .stroke, strokeCap: .round)
    let tapePaint = GPaint(color: HexColor.argb("#F8FAFC"), style: .stroke, strokeCap: .round)
    let bladeOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeCap: .round, strokeJoin: .round)
    let bladeTape = GPaint(color: HexColor.argb("#FFFFFF"), style: .stroke, strokeCap: .round, strokeJoin: .round)
    let puckScuffPaint = GPaint(color: HexColor.argb(160, 15, 23, 42))

    // Goalie sprite paints
    let padPaint = GPaint(color: HexColor.argb("#F8FAFC"))
    let padOutline = GPaint(color: HexColor.argb("#0F172A"), style: .stroke, strokeWidth: 0.12)
    let padPlate = GPaint(color: HexColor.argb("#E2E8F0"))
    let padCrease = GPaint(color: HexColor.argb(180, 100, 116, 139), style: .stroke, strokeWidth: 0.06)
    let padStripe = GPaint()
    let leatherPaint = GPaint(color: HexColor.argb("#78350F"))
    let leatherLight = GPaint(color: HexColor.argb("#B45309"))
    let trapperLace = GPaint(color: HexColor.argb("#FEF3C7"), style: .stroke, strokeWidth: 0.07)
    let blockerBevel = GPaint(color: HexColor.argb(120, 255, 255, 255), style: .stroke, strokeWidth: 0.08)
    let maskPaint = GPaint()
    let catEyeCage = GPaint(color: HexColor.argb("#CBD5E1"), style: .stroke, strokeWidth: 0.06)
    let starPaint = GPaint(color: HexColor.argb("#FDE047"), style: .stroke, strokeWidth: 0.14, strokeCap: .round)
    let sprayOuter = GPaint(color: HexColor.argb("#8FB3CC"))
    let sprayInner = GPaint(color: HexColor.white)
    let numberPaint = GPaint(textAlign: .center, textSize: 2.0, bold: true)
    let numberShadowPaint = GPaint(color: HexColor.argb(200, 0, 0, 0), textAlign: .center, textSize: 2.0, bold: true, style: .stroke, strokeWidth: 0.35)
    let ringPaint = GPaint(color: HexColor.argb("#FDE047"), style: .stroke, strokeWidth: 0.3)
    let ringGlow = GPaint(color: HexColor.argb(60, 253, 224, 71))
    let fireEmberPaint = GPaint()

    // Puck paints
    let puckPaint = GPaint(color: HexColor.argb("#09090B"))
    let puckKnurl = GPaint(color: HexColor.argb(140, 39, 39, 42), style: .stroke, strokeWidth: 0.08)
    let puckBevel = GPaint(color: HexColor.argb(120, 82, 82, 91), style: .stroke, strokeWidth: 0.08)
    let puckRim = GPaint(color: HexColor.argb("#27272A"), style: .stroke, strokeWidth: 0.12)
    let puckHalo = GPaint(color: HexColor.argb(180, 255, 255, 255))
    let cometTrail = GPaint(style: .stroke, strokeCap: .round)
    let cometGlow = GPaint()
    let meterBack = GPaint(color: HexColor.argb(140, 0, 0, 0), style: .stroke, strokeWidth: 0.5)
    let meterFill = GPaint(color: HexColor.argb("#F97316"), style: .stroke, strokeWidth: 0.5, strokeCap: .round)

    // HUD paints
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
    let shootoutDotFill = GPaint()
    let shootoutDotStroke = GPaint(style: .stroke, strokeWidth: 1.5)
    let ppBadgeBack = GPaint()
    let ppBadgeBorder = GPaint(color: HexColor.white, style: .stroke)
    let ppBadgeText = GPaint(textAlign: .center, bold: true)
    let switchShooterPaint = GPaint(color: HexColor.argb(235, 15, 23, 42))
    let switchShooterBorder = GPaint(color: HexColor.argb(220, 245, 158, 11), style: .stroke)
    let switchShooterTitle = GPaint(color: HexColor.argb("#FDE68A"), textAlign: .center, bold: true)
    let switchShooterSub = GPaint(color: HexColor.white, textAlign: .center, bold: true)
    var switchShooterRect = CGRect.zero

    // Per-team cached jersey shading
    private var shaderColor: [UInt32] = [0, 0]
    private var skaterShader: [RadialGradientSpec?] = [nil, nil]
    private var goalieShader: [RadialGradientSpec?] = [nil, nil]
    private var helmetColor: [UInt32] = [0, 0]
    private var gloveColor: [UInt32] = [0, 0]

    // Spray particles
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

    // Snowfall particles
    private var snowX = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowY = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowVx = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowVy = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowR = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowAlpha = [Int](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowSway = [CGFloat](repeating: 0, count: Renderer.MAX_SNOW)
    private var snowInitialized = false

    // Breath vapor
    private var breathX = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathY = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathVx = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathVy = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathLife = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathMax = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathR = [CGFloat](repeating: 0, count: Renderer.MAX_BREATH)
    private var breathHead = 0

    // Shattered glass
    private var shardX = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardY = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardVx = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardVy = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardRot = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardVrot = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardMax = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardLife = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var shardSize = [CGFloat](repeating: 0, count: Renderer.MAX_SHARDS)
    private var prevGlassTimer: Float = 0

    // Skate scratch marks
    private var scratchX1 = [CGFloat](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchY1 = [CGFloat](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchX2 = [CGFloat](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchY2 = [CGFloat](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchAlpha = [Int](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchWidth = [CGFloat](repeating: 0, count: Renderer.MAX_SCRATCHES)
    private var scratchCount = 0
    private var scratchNext = 0
    private var lastPhase: Phase = .faceoff

    // Puck comet trail
    private static let TRAIL_POINTS = 8
    private var puckTrailX = [CGFloat](repeating: 0, count: TRAIL_POINTS)
    private var puckTrailY = [CGFloat](repeating: 0, count: TRAIL_POINTS)
    private var puckTrailHead = 0

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
        buildWinterLandscape()
    }

    private func buildCrowd() {
        let pxPerFt: CGFloat = 5
        let bw = max(8, Int(crowdRect.width * pxPerFt))
        let bh = max(8, Int(crowdRect.height * pxPerFt))
        guard let ctx = CGContext(data: nil, width: bw, height: bh, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return }
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
        p.color = HexColor.argb("#0F1B2E")
        p.style = .stroke
        p.strokeWidth = 3
        canvas.drawRoundRect(CGRect(left: -Renderer.rinkHalfL - 2.5, top: -Renderer.rinkHalfW - 2.5, right: Renderer.rinkHalfL + 2.5, bottom: Renderer.rinkHalfW + 2.5), Renderer.rinkCornerR + 2.5, Renderer.rinkCornerR + 2.5, p)
        crowd = ctx.makeImage()
    }

    private func buildWinterLandscape() {
        let pxPerFt: CGFloat = 5
        let bw = max(8, Int(crowdRect.width * pxPerFt))
        let bh = max(8, Int(crowdRect.height * pxPerFt))
        guard let ctx = CGContext(data: nil, width: bw, height: bh, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return }
        ctx.translateBy(x: 0, y: CGFloat(bh))
        ctx.scaleBy(x: 1, y: -1)
        let canvas = GCanvas(context: ctx, bounds: CGRect(x: 0, y: 0, width: bw, height: bh))
        canvas.drawColor(HexColor.argb("#09101C"))
        let rng = SeededRandom(seed: 42)
        let p = GPaint()

        canvas.translate(CGFloat(bw) / 2, CGFloat(bh) / 2)
        canvas.scale(pxPerFt, pxPerFt)

        // Distant alpine mountain silhouettes
        let mountainPath = GPath()
        mountainPath.moveTo(-Renderer.worldHalfW - 5, -Renderer.worldHalfH)
        mountainPath.lineTo(-Renderer.worldHalfW - 5, -Renderer.worldHalfH + 8)
        var mx = -Renderer.worldHalfW
        while mx <= Renderer.worldHalfW + 10 {
            mountainPath.lineTo(mx, -Renderer.worldHalfH + 4 + CGFloat(rng.nextFloat()) * 7)
            mx += 12
        }
        mountainPath.lineTo(Renderer.worldHalfW + 5, -Renderer.worldHalfH)
        mountainPath.close()
        p.color = HexColor.argb("#152438")
        p.style = .fill
        canvas.drawPath(mountainPath, p)

        // Rolling snowdrifts
        let snowColors: [UInt32] = [HexColor.argb("#C3D9E9"), HexColor.argb("#D6E7F4"), HexColor.argb("#E7F3FA")]
        for layer in 0..<3 {
            p.color = snowColors[layer]
            let ringW = Renderer.rinkHalfL + 2.5 + CGFloat(2 - layer) * 5.5
            let ringH = Renderer.rinkHalfW + 2.5 + CGFloat(2 - layer) * 5.5
            let cr = Renderer.rinkCornerR + CGFloat(2 - layer) * 5.5
            canvas.drawRoundRect(CGRect(left: -ringW, top: -ringH, right: ringW, bottom: ringH), cr, cr, p)
        }

        // Pine trees in the snow perimeter
        let pineGreens: [UInt32] = [HexColor.argb("#0F261B"), HexColor.argb("#173727"), HexColor.argb("#1F4B35")]
        let snowCap = GPaint(color: HexColor.argb("#F4FAFC"))
        let trunkPaint = GPaint(color: HexColor.argb("#341F10"))

        var angle: Double = 0
        while angle < .pi * 2 {
            let dist = Renderer.rinkHalfW + 7 + CGFloat(rng.nextFloat()) * 12
            let tx = CGFloat(cos(angle)) * (Renderer.rinkHalfL + 8 + CGFloat(rng.nextFloat()) * 10)
            let ty = CGFloat(sin(angle)) * dist
            if abs(tx) <= Renderer.worldHalfW - 2 && abs(ty) <= Renderer.worldHalfH - 2 {
                let treeScale = 0.85 + CGFloat(rng.nextFloat()) * 0.65
                canvas.drawRect(CGRect(left: tx - 0.25 * treeScale, top: ty, right: tx + 0.25 * treeScale, bottom: ty + 1.2 * treeScale), trunkPaint)
                for tier in 0...2 {
                    let tierY = ty - CGFloat(tier) * 1.0 * treeScale
                    let tierW = (2.2 - CGFloat(tier) * 0.55) * treeScale
                    let treePath = GPath()
                    treePath.moveTo(tx, tierY - 1.2 * treeScale)
                    treePath.lineTo(tx - tierW / 2, tierY)
                    treePath.lineTo(tx + tierW / 2, tierY)
                    treePath.close()
                    p.color = pineGreens[rng.nextInt(pineGreens.count)]
                    canvas.drawPath(treePath, p)

                    let capPath = GPath()
                    capPath.moveTo(tx, tierY - 1.2 * treeScale)
                    capPath.lineTo(tx - tierW * 0.35, tierY - 0.4 * treeScale)
                    capPath.lineTo(tx + tierW * 0.35, tierY - 0.4 * treeScale)
                    capPath.close()
                    canvas.drawPath(capPath, snowCap)
                }
            }
            angle += 0.16 + Double(rng.nextFloat()) * 0.08
        }

        // Rustic cabin in corner
        let cabinX = -Renderer.rinkHalfL - 8
        let cabinY = -Renderer.rinkHalfW - 7
        p.color = HexColor.argb("#422A1A")
        canvas.drawRect(CGRect(left: cabinX - 3.5, top: cabinY - 2.5, right: cabinX + 3.5, bottom: cabinY + 2.5), p)
        let roofPath = GPath()
        roofPath.moveTo(cabinX - 4.2, cabinY - 2.5)
        roofPath.lineTo(cabinX, cabinY - 5.2)
        roofPath.lineTo(cabinX + 4.2, cabinY - 2.5)
        roofPath.close()
        p.color = HexColor.argb("#EDF5FA")
        canvas.drawPath(roofPath, p)
        let glowPaint = GPaint(color: HexColor.argb("#F59E0B"))
        canvas.drawRect(CGRect(left: cabinX - 1.5, top: cabinY - 0.5, right: cabinX - 0.3, bottom: cabinY + 0.9), glowPaint)
        canvas.drawRect(CGRect(left: cabinX + 0.3, top: cabinY - 0.5, right: cabinX + 1.5, bottom: cabinY + 0.9), glowPaint)

        winterLandscape = ctx.makeImage()
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
        updateSnow(dtf)
        updateBreath(world, dtf)
        updateGlassShards(world, dtf)

        // Zamboni resurfacing between periods
        if world.phase == .periodEnd && lastPhase != .periodEnd {
            scratchCount = 0
            scratchNext = 0
        }
        lastPhase = world.phase

        // Accumulate subtle skate scratches during play
        if world.phase == .play {
            for s in world.allSkaters {
                if s.speed > 11 && sprayRng.nextFloat() < 0.12 {
                    let idx = scratchNext
                    scratchX1[idx] = CGFloat(s.x) + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.8
                    scratchY1[idx] = CGFloat(s.y) + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.8
                    let angle = CGFloat(s.facing) + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.6
                    let len: CGFloat = 1.0 + CGFloat(sprayRng.nextFloat()) * 2.0
                    scratchX2[idx] = scratchX1[idx] + cos(angle) * len
                    scratchY2[idx] = scratchY1[idx] + sin(angle) * len
                    scratchAlpha[idx] = 20 + sprayRng.nextInt(40)
                    scratchWidth[idx] = 0.08 + CGFloat(sprayRng.nextFloat()) * 0.08
                    scratchNext = (scratchNext + 1) % Renderer.MAX_SCRATCHES
                    if scratchCount < Renderer.MAX_SCRATCHES { scratchCount += 1 }
                }
            }
        }

        let isPond = world.arenaType == .winterPond
        canvas.drawColor(isPond ? HexColor.argb("#09101C") : HexColor.argb("#0B1424"))
        canvas.save()
        camera.apply(canvas)

        if isPond {
            if let winterLandscape = winterLandscape { canvas.drawImage(winterLandscape, in: crowdRect) }
        } else {
            if let crowd = crowd { canvas.drawImage(crowd, in: crowdRect) }
        }

        drawRink(canvas, world)

        if world.phase == .faceoff {
            let r: CGFloat = 2.2 + 0.6 * sin(animTime * 8)
            faceoffPulse.alpha = 200
            canvas.drawCircle(CGFloat(world.faceoffX), CGFloat(world.faceoffY), r, faceoffPulse)
        }

        for s in world.allSkaters {
            if !world.isShootout || abs(s.y) < 45 { drawShadow(canvas, s) }
        }
        drawSpray(canvas)

        let sorted = world.allSkaters.filter { !world.isShootout || abs($0.y) < 45 }.sorted { $0.y < $1.y }
        let controlled: Skater? = localTeam >= 0 ? world.controlledSkater(localTeam) : nil
        for s in sorted {
            drawSkater(canvas, world, s, s === controlled, localTeam >= 0 ? CGFloat(world.shotCharge[localTeam]) : 0)
        }

        drawPuck(canvas, world.puck)
        drawBreath(canvas)
        drawGlassShards(canvas, world)
        if isPond {
            drawSnow(canvas)
        }
        canvas.restore()

        drawScoreboard(canvas, world)
        drawShootoutControls(canvas, world, localTeam)
        drawBanner(canvas, world)
        if let controls = controls { drawControls(canvas, world, localTeam, controls) }
        drawPauseButton(canvas)
    }

    // ================================================================ rink

    private func drawRink(_ canvas: GCanvas, _ world: World) {
        let isPond = world.arenaType == .winterPond
        canvas.drawPath(rinkPath, isPond ? pondBoardsPaint : boardsPaint)
        canvas.drawPath(rinkPath, isPond ? pondIcePaint : icePaint)
        canvas.save()
        canvas.clipPath(rinkPath)

        // Skate scratch marks on ice
        for i in 0..<scratchCount {
            scratchPaint.alpha = scratchAlpha[i]
            scratchPaint.strokeWidth = scratchWidth[i]
            canvas.drawLine(scratchX1[i], scratchY1[i], scratchX2[i], scratchY2[i], scratchPaint)
        }

        if isPond {
            // Natural frozen lake ice veins
            canvas.drawLine(-25, -14, -5, 6, pondCrackPaint)
            canvas.drawLine(-5, 6, 18, 14, pondCrackPaint)
            canvas.drawLine(18, 14, 32, 11, pondCrackPaint)
            canvas.drawLine(-55, 12, -30, 24, pondCrackPaint)
            canvas.drawLine(35, -22, 62, -8, pondCrackPaint)
            canvas.drawLine(-12, -25, 8, -18, pondCrackPaint)
        }

        // Faint zone shading toward the ends.
        let shade = isPond ? pondIceShadePaint : iceShadePaint
        tmpRect = CGRect(left: -Renderer.rinkHalfL, top: -Renderer.rinkHalfW, right: -Renderer.goalLineX, bottom: Renderer.rinkHalfW)
        canvas.drawRect(tmpRect, shade)
        tmpRect = CGRect(left: Renderer.goalLineX, top: -Renderer.rinkHalfW, right: Renderer.rinkHalfL, bottom: Renderer.rinkHalfW)
        canvas.drawRect(tmpRect, shade)

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

        // Red goal siren beacons behind nets when goal is scored
        if world.phase == .goal {
            let pulse = sin(animTime * 14) * 0.5 + 0.5
            let redAlpha = Int(130 + pulse * 125)
            sirenPaint.color = HexColor.argb(redAlpha, 255, 20, 20)
            for e: CGFloat in [-1, 1] {
                let beaconX = e * (Renderer.goalLineX + Renderer.netDepth + 1.2)
                let beaconY: CGFloat = 0
                canvas.drawCircle(beaconX, beaconY, 1.4 + pulse * 0.6, sirenPaint)
                canvas.save()
                canvas.translate(beaconX, beaconY)
                let beamAngle = animTime * 7 * e
                canvas.rotate(beamAngle * 180 / .pi)
                sirenBeam.color = HexColor.argb(Int(50 + pulse * 70), 255, 40, 40)
                tmpPath.reset()
                tmpPath.moveTo(0, 0)
                tmpPath.lineTo(14, -4.5)
                tmpPath.lineTo(14, 4.5)
                tmpPath.close()
                canvas.drawPath(tmpPath, sirenBeam)
                tmpPath.reset()
                tmpPath.moveTo(0, 0)
                tmpPath.lineTo(-14, -4.5)
                tmpPath.lineTo(-14, 4.5)
                tmpPath.close()
                canvas.drawPath(tmpPath, sirenBeam)
                canvas.restore()
            }
        }
        canvas.restore()

        // Boards: kick plate inside, glass outside.
        if isPond {
            canvas.drawPath(rinkPath, pondKickPlate)
            tmpRect = rinkRect.insetBy(dx: -1.1, dy: -1.1)
            tmpPath.reset()
            tmpPath.addRoundRect(tmpRect, Renderer.rinkCornerR + 1.1, Renderer.rinkCornerR + 1.1)
            canvas.drawPath(tmpPath, pondBoardsPaint)
            tmpRect = tmpRect.insetBy(dx: -0.8, dy: -0.8)
            tmpPath.reset()
            tmpPath.addRoundRect(tmpRect, Renderer.rinkCornerR + 1.9, Renderer.rinkCornerR + 1.9)
            canvas.drawPath(tmpPath, pondSnowCapPaint)
        } else {
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

    // ================================================================ snowfall

    private func updateSnow(_ dt: CGFloat) {
        if !snowInitialized {
            for i in 0..<Renderer.MAX_SNOW {
                snowX[i] = (CGFloat(sprayRng.nextFloat()) - 0.5) * 2 * Renderer.worldHalfW
                snowY[i] = (CGFloat(sprayRng.nextFloat()) - 0.5) * 2 * Renderer.worldHalfH
                snowVy[i] = 4.5 + CGFloat(sprayRng.nextFloat()) * 6.0
                snowVx[i] = -0.5 + CGFloat(sprayRng.nextFloat()) * 1.0
                snowR[i] = 0.22 + CGFloat(sprayRng.nextFloat()) * 0.42
                snowAlpha[i] = 110 + sprayRng.nextInt(125)
                snowSway[i] = CGFloat(sprayRng.nextFloat()) * 6.28
            }
            snowInitialized = true
        }
        for i in 0..<Renderer.MAX_SNOW {
            snowY[i] += snowVy[i] * dt
            snowX[i] += (snowVx[i] + sin(animTime * 1.5 + snowSway[i]) * 1.2) * dt
            if snowY[i] > Renderer.worldHalfH {
                snowY[i] = -Renderer.worldHalfH
                snowX[i] = (CGFloat(sprayRng.nextFloat()) - 0.5) * 2 * Renderer.worldHalfW
            }
            if snowX[i] > Renderer.worldHalfW { snowX[i] = -Renderer.worldHalfW }
            if snowX[i] < -Renderer.worldHalfW { snowX[i] = Renderer.worldHalfW }
        }
    }

    private func drawSnow(_ canvas: GCanvas) {
        for i in 0..<Renderer.MAX_SNOW {
            snowflakePaint.alpha = snowAlpha[i]
            canvas.drawCircle(snowX[i], snowY[i], snowR[i], snowflakePaint)
        }
    }

    // ================================================================ skater breath vapor

    private func updateBreath(_ world: World, _ dt: CGFloat) {
        for i in 0..<Renderer.MAX_BREATH {
            if breathLife[i] <= 0 { continue }
            breathLife[i] -= dt
            breathX[i] += breathVx[i] * dt
            breathY[i] += breathVy[i] * dt
            let drag = exp(-dt * 3.5)
            breathVx[i] *= drag
            breathVy[i] *= drag
        }

        guard world.arenaType == .winterPond else { return }

        for s in world.allSkaters {
            s.breathTimer -= Float(dt)
            if s.breathTimer <= 0 {
                s.breathTimer = 1.3 + sprayRng.nextFloat() * 1.2
                let hx = CGFloat(s.x) + cos(CGFloat(s.facing)) * 0.9
                let hy = CGFloat(s.y) + sin(CGFloat(s.facing)) * 0.9
                for _ in 0...1 {
                    let idx = breathHead
                    breathHead = (breathHead + 1) % Renderer.MAX_BREATH
                    breathX[idx] = hx + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.25
                    breathY[idx] = hy + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.25
                    let sp = 1.2 + CGFloat(sprayRng.nextFloat()) * 1.4
                    let angle = CGFloat(s.facing) + (CGFloat(sprayRng.nextFloat()) - 0.5) * 0.5
                    breathVx[idx] = cos(angle) * sp + CGFloat(s.vx) * 0.25
                    breathVy[idx] = sin(angle) * sp + CGFloat(s.vy) * 0.25
                    breathMax[idx] = 0.55 + CGFloat(sprayRng.nextFloat()) * 0.25
                    breathLife[idx] = breathMax[idx]
                    breathR[idx] = 0.22 + CGFloat(sprayRng.nextFloat()) * 0.12
                }
            }
        }
    }

    private func drawBreath(_ canvas: GCanvas) {
        for i in 0..<Renderer.MAX_BREATH {
            let life = breathLife[i]
            if life <= 0 { continue }
            let frac = min(max(life / breathMax[i], 0), 1)
            let rad = breathR[i] + (1 - frac) * 0.45
            breathPaint.alpha = Int(130 * frac)
            canvas.drawCircle(breathX[i], breathY[i], rad, breathPaint)
        }
    }

    // ================================================================ shattered glass

    private func updateGlassShards(_ world: World, _ dt: CGFloat) {
        if world.glassShatterTimer > 3.8 && prevGlassTimer <= 3.8 {
            let sx = CGFloat(world.glassShatterX)
            let sy = CGFloat(world.glassShatterY)
            var nx = -sx
            var ny = -sy
            let len = max(hypot(nx, ny), 0.1)
            nx /= len; ny /= len

            for i in 0..<Renderer.MAX_SHARDS {
                shardX[i] = sx + (CGFloat(sprayRng.nextFloat()) - 0.5) * 1.8
                shardY[i] = sy + (CGFloat(sprayRng.nextFloat()) - 0.5) * 1.8
                let cone = (CGFloat(sprayRng.nextFloat()) - 0.5) * 2.0
                let sp = 11 + CGFloat(sprayRng.nextFloat()) * 22
                let c = cos(cone), sn = sin(cone)
                shardVx[i] = (nx * c - ny * sn) * sp
                shardVy[i] = (nx * sn + ny * c) * sp
                shardRot[i] = CGFloat(sprayRng.nextFloat()) * 360
                shardVrot[i] = (CGFloat(sprayRng.nextFloat()) - 0.5) * 900
                shardMax[i] = 1.8 + CGFloat(sprayRng.nextFloat()) * 1.8
                shardLife[i] = shardMax[i]
                shardSize[i] = 0.32 + CGFloat(sprayRng.nextFloat()) * 0.52
            }
        }
        prevGlassTimer = world.glassShatterTimer

        for i in 0..<Renderer.MAX_SHARDS {
            if shardLife[i] <= 0 { continue }
            shardLife[i] -= dt
            shardX[i] += shardVx[i] * dt
            shardY[i] += shardVy[i] * dt
            shardRot[i] += shardVrot[i] * dt
            let drag = exp(-dt * 2.2)
            shardVx[i] *= drag
            shardVy[i] *= drag
            shardVrot[i] *= drag
        }
    }

    private func drawGlassShards(_ canvas: GCanvas, _ world: World) {
        if world.glassShatterTimer > 0 {
            let frac = min(max(CGFloat(world.glassShatterTimer) / 4.0, 0), 1)
            let alpha = Int(240 * frac)
            glassSpiderwebPaint.alpha = alpha
            glassSpiderwebFill.alpha = Int(50 * frac)
            let cx = CGFloat(world.glassShatterX)
            let cy = CGFloat(world.glassShatterY)

            canvas.drawCircle(cx, cy, 1.8, glassSpiderwebFill)

            let rings: [CGFloat] = [1.2, 2.6, 4.2]
            for r in rings {
                tmpPath.reset()
                for s in 0...7 {
                    let a = Double(s) * (.pi / 4) + Double(s % 2) * 0.15
                    let dist = r * (0.8 + CGFloat(s % 3) * 0.18)
                    let px = cx + CGFloat(cos(a)) * dist
                    let py = cy + CGFloat(sin(a)) * dist
                    if s == 0 { tmpPath.moveTo(px, py) } else { tmpPath.lineTo(px, py) }
                }
                tmpPath.close()
                canvas.drawPath(tmpPath, glassSpiderwebPaint)
            }

            for s in 0...11 {
                let a = Double(s) * (.pi / 6) + Double((s * 7) % 5) * 0.08
                let len: CGFloat = 3.5 + CGFloat((s * 11) % 4) * 1.5
                let ex = cx + CGFloat(cos(a)) * len
                let ey = cy + CGFloat(sin(a)) * len
                canvas.drawLine(cx, cy, ex, ey, glassSpiderwebPaint)
            }
        }

        for i in 0..<Renderer.MAX_SHARDS {
            let life = shardLife[i]
            if life <= 0 { continue }
            let frac = min(max(life / shardMax[i], 0), 1)
            let sz = shardSize[i]
            canvas.save()
            canvas.translate(shardX[i], shardY[i])
            canvas.rotate(shardRot[i])
            glassShardFill.alpha = Int(190 * frac)
            glassShardEdge.alpha = Int(250 * frac)
            tmpPath.reset()
            tmpPath.moveTo(-sz * 0.6, -sz * 0.9)
            tmpPath.lineTo(sz * 0.9, -sz * 0.2)
            tmpPath.lineTo(sz * 0.2, sz * 0.9)
            tmpPath.close()
            canvas.drawPath(tmpPath, glassShardFill)
            canvas.drawPath(tmpPath, glassShardEdge)
            if frac > 0.3 && i % 3 == 0 {
                snowflakePaint.alpha = Int(230 * frac)
                canvas.drawCircle(0, 0, sz * 0.18, snowflakePaint)
            }
            canvas.restore()
        }
    }

    func release() {
        crowd = nil
        winterLandscape = nil
    }
}
