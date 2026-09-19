import CoreGraphics

/// Minimal Paint/Path shim modeled on the subset of android.graphics.{Paint,Path}
/// that Renderer.kt uses, so the Renderer port can stay close to line-for-line
/// instead of hand-writing CGContext boilerplate at every draw call.

enum PaintStyle {
    case fill, stroke, fillAndStroke
}

enum LineCap {
    case butt, round, square

    var cg: CGLineCap {
        switch self {
        case .butt: return .butt
        case .round: return .round
        case .square: return .square
        }
    }
}

enum LineJoin {
    case miter, round, bevel

    var cg: CGLineJoin {
        switch self {
        case .miter: return .miter
        case .round: return .round
        case .bevel: return .bevel
        }
    }
}

enum GTextAlign {
    case left, center, right
}

/// A radial gradient, matching the one Shader kind Renderer.kt uses (jersey
/// shading). Coordinates are in the same local space the gradient is defined
/// in and drawn at -- i.e. whatever the CTM is at draw time, matching how
/// android.graphics.Shader coordinates work.
struct RadialGradientSpec {
    let colors: [UInt32]
    let stops: [CGFloat]
    let centerX: CGFloat
    let centerY: CGFloat
    let radius: CGFloat
}

/// Mutable paint bag, matching how Renderer.kt reuses a fixed set of Paint
/// objects and mutates .color / .alpha / .strokeWidth before each draw call.
final class GPaint {
    var color: UInt32
    var style: PaintStyle
    var strokeWidth: CGFloat
    var strokeCap: LineCap
    var strokeJoin: LineJoin
    var textAlign: GTextAlign
    var textSize: CGFloat
    var bold: Bool
    /// When set, fills use this radial gradient instead of a flat color
    /// (matches assigning `paint.shader = ...` on Android).
    var shader: RadialGradientSpec?

    init(
        color: UInt32 = HexColor.white,
        style: PaintStyle = .fill,
        strokeWidth: CGFloat = 0,
        strokeCap: LineCap = .butt,
        strokeJoin: LineJoin = .miter,
        textAlign: GTextAlign = .left,
        textSize: CGFloat = 12,
        bold: Bool = false
    ) {
        self.color = color
        self.style = style
        self.strokeWidth = strokeWidth
        self.strokeCap = strokeCap
        self.strokeJoin = strokeJoin
        self.textAlign = textAlign
        self.textSize = textSize
        self.bold = bold
    }

    /// Matches Paint.alpha: overwrites only the alpha byte of `color`.
    var alpha: Int {
        get { Int((color >> 24) & 0xFF) }
        set { color = (color & 0x00FF_FFFF) | (UInt32(min(max(newValue, 0), 255)) << 24) }
    }

    var cgColor: CGColor { HexColor.cgColor(color) }
}

/// Wraps a CGMutablePath, matching the small subset of android.graphics.Path
/// (moveTo/lineTo/quadTo/addRoundRect/reset) Renderer.kt uses as scratch state.
final class GPath {
    private(set) var cgPath = CGMutablePath()

    func reset() { cgPath = CGMutablePath() }

    func moveTo(_ x: CGFloat, _ y: CGFloat) { cgPath.move(to: CGPoint(x: x, y: y)) }
    func lineTo(_ x: CGFloat, _ y: CGFloat) { cgPath.addLine(to: CGPoint(x: x, y: y)) }

    /// Matches Path.quadTo(controlX, controlY, endX, endY).
    func quadTo(_ cx: CGFloat, _ cy: CGFloat, _ x: CGFloat, _ y: CGFloat) {
        cgPath.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: cx, y: cy))
    }

    func addRoundRect(_ rect: CGRect, _ rx: CGFloat, _ ry: CGFloat) {
        cgPath.addRoundedRect(in: rect, cornerWidth: max(rx, 0.0001), cornerHeight: max(ry, 0.0001))
    }
}

/// Matches android.graphics.RectF's (left, top, right, bottom) shape, since
/// Renderer.kt builds every rect that way rather than from origin + size.
extension CGRect {
    init(left: CGFloat, top: CGFloat, right: CGFloat, bottom: CGFloat) {
        self.init(x: left, y: top, width: right - left, height: bottom - top)
    }
}
