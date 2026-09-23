import Foundation

/// Colours as packed 0xAARRGGBB, matching android.graphics.Color's int
/// representation so the eventual Renderer port can convert to CGColor/UIColor.
enum HexColor {
    static func argb(_ hex: String) -> UInt32 {
        var s = hex
        if s.hasPrefix("#") { s.removeFirst() }
        var value: UInt64 = 0
        Scanner(string: s).scanHexInt64(&value)
        return 0xFF000000 | (UInt32(value) & 0x00FFFFFF)
    }

    static let white: UInt32 = 0xFFFFFFFF
}
