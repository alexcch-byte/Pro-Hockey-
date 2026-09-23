import Foundation

/// Transport-agnostic delegate for receiving host network events.
public protocol HostLinkListener: AnyObject {
    func onClientConnected()
    func onClientDisconnected()
    func onMessage(_ json: [String: Any])
}

/// Transport-agnostic host side of a multiplayer hockey match.
public protocol HostLink: AnyObject {
    var listener: HostLinkListener? { get set }
    var stateHz: Int { get }
    func send(_ json: String)
    func stop()
}

/// Transport-agnostic delegate for receiving guest network events.
public protocol GuestLinkListener: AnyObject {
    func onConnected()
    func onConnectFailed(reason: String)
    func onDisconnected()
    func onMessage(_ json: [String: Any])
}

/// Transport-agnostic guest side of a multiplayer hockey match.
public protocol GuestLink: AnyObject {
    var listener: GuestLinkListener? { get set }
    var inputHz: Int { get }
    func send(_ json: String)
    func disconnect()
}
