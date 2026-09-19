import CoreGraphics
import Foundation

#if canImport(UIKit)
import UIKit
private typealias PlatformFont = UIFont
#elseif canImport(AppKit)
import AppKit
private typealias PlatformFont = NSFont
#endif

/// Wraps a CGContext with an android.graphics.Canvas-shaped API, so
/// Renderer.swift can stay a close, mechanical port of Renderer.kt instead of
/// hand-writing CGContext boilerplate at every one of its ~300 draw calls.
///
/// Must be used only while `context` is the platform's *current* graphics
/// context (i.e. from inside a UIView/NSView draw override, or immediately
/// after creating an offscreen bitmap context) -- text drawing pushes it as
/// current explicitly (see drawText), but transforms and shape drawing act
/// on `context` directly regardless.
final class GCanvas {
    let context: CGContext
    /// Full drawable bounds in points, used by drawColor to clear the whole
    /// surface (Android's canvas.drawColor ignores any clip).
    let bounds: CGRect

    init(context: CGContext, bounds: CGRect) {
        self.context = context
        self.bounds = bounds
    }

    // ---------------------------------------------------------------- state

    func save() { context.saveGState() }
    func restore() { context.restoreGState() }
    func translate(_ dx: CGFloat, _ dy: CGFloat) { context.translateBy(x: dx, y: dy) }
    func scale(_ sx: CGFloat, _ sy: CGFloat) { context.scaleBy(x: sx, y: sy) }
    /// Matches Canvas.rotate(degrees).
    func rotate(_ degrees: CGFloat) { context.rotate(by: degrees * .pi / 180) }

    func clipPath(_ path: GPath) {
        context.addPath(path.cgPath)
        context.clip()
    }

    // ---------------------------------------------------------------- fills

    func drawColor(_ color: UInt32) {
        context.setFillColor(HexColor.cgColor(color))
        context.fill(bounds)
    }

    func drawCircle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ paint: GPaint) {
        drawEllipse(CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2), paint)
    }

    func drawOval(_ rect: CGRect, _ paint: GPaint) {
        drawEllipse(rect, paint)
    }

    func drawRect(_ rect: CGRect, _ paint: GPaint) {
        drawShape(CGPath(rect: rect, transform: nil), paint)
    }

    func drawRoundRect(_ rect: CGRect, _ rx: CGFloat, _ ry: CGFloat, _ paint: GPaint) {
        drawShape(CGPath(roundedRect: rect, cornerWidth: max(rx, 0.0001), cornerHeight: max(ry, 0.0001), transform: nil), paint)
    }

    func drawPath(_ path: GPath, _ paint: GPaint) {
        drawShape(path.cgPath, paint)
    }

    /// Always strokes, matching Canvas.drawLine (paint.style is ignored).
    func drawLine(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ paint: GPaint) {
        applyStroke(paint)
        context.beginPath()
        context.move(to: CGPoint(x: x1, y: y1))
        context.addLine(to: CGPoint(x: x2, y: y2))
        context.strokePath()
    }

    /// Matches Canvas.drawArc: startDeg=0 is the +x axis, positive sweep goes
    /// from there toward +y (i.e. "clockwise" in this y-down world -- the
    /// same sign convention every angle in the engine already uses via
    /// cos/sin). Approximated as a polyline rather than using CGContext's
    /// native arc primitive, whose "clockwise" flag has an easy-to-invert
    /// meaning once a flipped CTM is involved; a polyline built from the same
    /// cos/sin math the rest of the engine uses can't have that class of bug.
    func drawArc(_ rect: CGRect, _ startDeg: CGFloat, _ sweepDeg: CGFloat, _ useCenter: Bool, _ paint: GPaint) {
        let cx = rect.midX, cy = rect.midY
        let rx = rect.width / 2, ry = rect.height / 2
        let segments = max(2, Int(abs(sweepDeg) / 4) + 1)
        let path = CGMutablePath()
        if useCenter { path.move(to: CGPoint(x: cx, y: cy)) }
        for i in 0...segments {
            let t = CGFloat(i) / CGFloat(segments)
            let deg = startDeg + sweepDeg * t
            let rad = deg * .pi / 180
            let p = CGPoint(x: cx + cos(rad) * rx, y: cy + sin(rad) * ry)
            if i == 0 && !useCenter { path.move(to: p) } else { path.addLine(to: p) }
        }
        if useCenter { path.closeSubpath() }
        drawShape(path, paint)
    }

    // ------------------------------------------------------------ internals

    private func drawEllipse(_ rect: CGRect, _ paint: GPaint) {
        if let shader = paint.shader {
            context.saveGState()
            context.addEllipse(in: rect)
            context.clip()
            drawShader(shader)
            context.restoreGState()
            return
        }
        switch paint.style {
        case .fill:
            applyFill(paint); context.fillEllipse(in: rect)
        case .stroke:
            applyStroke(paint); context.strokeEllipse(in: rect)
        case .fillAndStroke:
            applyFill(paint); context.fillEllipse(in: rect)
            applyStroke(paint); context.strokeEllipse(in: rect)
        }
    }

    private func drawShape(_ path: CGPath, _ paint: GPaint) {
        if let shader = paint.shader {
            context.saveGState()
            context.addPath(path)
            context.clip()
            drawShader(shader)
            context.restoreGState()
            return
        }
        switch paint.style {
        case .fill:
            applyFill(paint); context.addPath(path); context.fillPath()
        case .stroke:
            applyStroke(paint); context.addPath(path); context.strokePath()
        case .fillAndStroke:
            applyFill(paint); context.addPath(path); context.fillPath()
            applyStroke(paint); context.addPath(path); context.strokePath()
        }
    }

    private func applyFill(_ paint: GPaint) {
        context.setFillColor(paint.cgColor)
    }

    private func applyStroke(_ paint: GPaint) {
        context.setStrokeColor(paint.cgColor)
        context.setLineWidth(paint.strokeWidth)
        context.setLineCap(paint.strokeCap.cg)
        context.setLineJoin(paint.strokeJoin.cg)
    }

    private func drawShader(_ spec: RadialGradientSpec) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: spec.colors.map { HexColor.cgColor($0) } as CFArray, locations: spec.stops) else { return }
        let center = CGPoint(x: spec.centerX, y: spec.centerY)
        context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: spec.radius, options: [.drawsAfterEndLocation])
    }

    /// Draws a CGImage into this (y-down) context without the vertical
    /// mirroring CGContext.draw(_:in:) otherwise produces under a flipped
    /// CTM: it locally re-flips just for this call, mirrored around the
    /// target rect's own vertical center.
    func drawImage(_ image: CGImage, in rect: CGRect) {
        context.saveGState()
        context.translateBy(x: 0, y: rect.origin.y * 2 + rect.height)
        context.scaleBy(x: 1, y: -1)
        context.draw(image, in: rect)
        context.restoreGState()
    }

    // ------------------------------------------------------------------ text

    /// Matches Canvas.drawText: (x, y) is the text baseline, horizontally
    /// positioned per paint.textAlign.
    func drawText(_ text: String, _ x: CGFloat, _ y: CGFloat, _ paint: GPaint) {
        #if canImport(UIKit) || canImport(AppKit)
        let font: PlatformFont = paint.bold ? .boldSystemFont(ofSize: paint.textSize) : .systemFont(ofSize: paint.textSize)
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: platformColor(paint.color)
        ]
        let attributed = NSAttributedString(string: text, attributes: attrs)
        let size = attributed.size()
        var drawX = x
        switch paint.textAlign {
        case .left: break
        case .center: drawX -= size.width / 2
        case .right: drawX -= size.width
        }
        #if canImport(UIKit)
        let drawY = y - font.ascender
        UIGraphicsPushContext(context)
        attributed.draw(at: CGPoint(x: drawX, y: drawY))
        UIGraphicsPopContext()
        #elseif canImport(AppKit)
        let drawY = y - font.ascender
        let previous = NSGraphicsContext.current
        NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: true)
        attributed.draw(at: CGPoint(x: drawX, y: drawY))
        NSGraphicsContext.current = previous
        #endif
        #endif
    }

    #if canImport(UIKit)
    private func platformColor(_ argb: UInt32) -> UIColor {
        UIColor(red: CGFloat(HexColor.red(argb)) / 255, green: CGFloat(HexColor.green(argb)) / 255, blue: CGFloat(HexColor.blue(argb)) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }
    #elseif canImport(AppKit)
    private func platformColor(_ argb: UInt32) -> NSColor {
        NSColor(red: CGFloat(HexColor.red(argb)) / 255, green: CGFloat(HexColor.green(argb)) / 255, blue: CGFloat(HexColor.blue(argb)) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }
    #endif
}
