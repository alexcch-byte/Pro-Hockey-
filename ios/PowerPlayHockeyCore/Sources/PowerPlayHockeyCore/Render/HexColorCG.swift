import CoreGraphics

/// android.graphics.Color-style component helpers plus a CGColor bridge,
/// operating on the same packed 0xAARRGGBB representation HexColor.argb produces.
extension HexColor {
    static func cgColor(_ argb: UInt32) -> CGColor {
        let a = CGFloat((argb >> 24) & 0xFF) / 255
        let r = CGFloat((argb >> 16) & 0xFF) / 255
        let g = CGFloat((argb >> 8) & 0xFF) / 255
        let b = CGFloat(argb & 0xFF) / 255
        return CGColor(red: r, green: g, blue: b, alpha: a)
    }

    static func argb(_ a: Int, _ r: Int, _ g: Int, _ b: Int) -> UInt32 {
        (UInt32(clampByte(a)) << 24) | (UInt32(clampByte(r)) << 16) | (UInt32(clampByte(g)) << 8) | UInt32(clampByte(b))
    }

    static func rgb(_ r: Int, _ g: Int, _ b: Int) -> UInt32 { argb(255, r, g, b) }

    static func red(_ argb: UInt32) -> Int { Int((argb >> 16) & 0xFF) }
    static func green(_ argb: UInt32) -> Int { Int((argb >> 8) & 0xFF) }
    static func blue(_ argb: UInt32) -> Int { Int(argb & 0xFF) }

    private static func clampByte(_ v: Int) -> Int { min(max(v, 0), 255) }
}
