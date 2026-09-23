import Foundation

/// Holds the live host / guest link set up in the multiplayer lobby so GameView can pick it up.
/// Works across both local Bonjour/WiFi and Cloudflare WebSocket internet relay.
public final class NetworkSession {
    public static let shared = NetworkSession()

    public var host: HostLink?
    public var guest: GuestLink?

    private init() {}

    public func clear() {
        host?.stop()
        guest?.disconnect()
        host = null
        guest = null
    }
}
