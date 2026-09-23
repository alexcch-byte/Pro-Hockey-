import Foundation

private let CODE_ALPHABET = Array("ABCDEFGHJKMNPQRSTUVWXYZ23456789")

private func randomRoomCode(length: Int = 5) -> String {
    String((0..<length).compactMap { _ in CODE_ALPHABET.randomElement() })
}

private func roomSocketURL(role: String, code: String) -> URL? {
    var wsBase = RELAY_BASE_URL
    if wsBase.hasPrefix("https://") {
        wsBase = "wss://" + wsBase.dropFirst("https://".count)
    } else if wsBase.hasPrefix("http://") {
        wsBase = "ws://" + wsBase.dropFirst("http://".count)
    }
    return URL(string: "\(wsBase)/room/\(code)?role=\(role)")
}

/// Host side of an internet match relayed through the Cloudflare Worker relay server.
/// Exposes room code and communicates through WebSocket.
public final class RelayHost: HostLink {
    public weak var listener: HostLinkListener?
    public let stateHz: Int = 30
    public let roomCode: String

    private var webSocketTask: URLSessionWebSocketTask?
    private var isRunning = false

    public init(roomCode: String = randomRoomCode()) {
        self.roomCode = roomCode
    }

    public func connect() {
        guard let url = roomSocketURL(role: "host", code: roomCode) else { return }
        isRunning = true
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()
        receiveNext()
    }

    private func receiveNext() {
        guard isRunning, let task = webSocketTask else { return }
        task.receive { [weak self] result in
            guard let self = self, self.isRunning else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.receiveNext()

            case .failure(let error):
                print("RelayHost receive failed: \(error)")
                DispatchQueue.main.async {
                    self.listener?.onClientDisconnected()
                }
            }
        }
    }

    private func handleMessage(_ text: String) {
        guard let dict = NetCodec.parse(text) else { return }
        let type = dict["t"] as? String ?? ""
        DispatchQueue.main.async { [weak self] in
            switch type {
            case "_hello":
                self?.listener?.onClientConnected()
            case "_bye":
                self?.listener?.onClientDisconnected()
            default:
                self?.listener?.onMessage(dict)
            }
        }
    }

    public func send(_ json: String) {
        webSocketTask?.send(.string(json)) { error in
            if let error = error {
                print("RelayHost send error: \(error)")
            }
        }
    }

    public func stop() {
        isRunning = false
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
    }
}

/// Guest side of an internet match: joins a room by code over the WebSocket relay.
public final class RelayGuest: GuestLink {
    public weak var listener: GuestLinkListener?
    public let inputHz: Int = 30

    private var webSocketTask: URLSessionWebSocketTask?
    private var isRunning = false

    public init() {}

    public func connect(roomCode: String) {
        guard let url = roomSocketURL(role: "guest", code: roomCode.uppercased()) else {
            listener?.onConnectFailed(reason: "Invalid room URL")
            return
        }
        isRunning = true
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        DispatchQueue.main.async { [weak self] in
            self?.listener?.onConnected()
        }

        receiveNext()
    }

    private func receiveNext() {
        guard isRunning, let task = webSocketTask else { return }
        task.receive { [weak self] result in
            guard let self = self, self.isRunning else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.receiveNext()

            case .failure(let error):
                print("RelayGuest receive failed: \(error)")
                DispatchQueue.main.async {
                    self.listener?.onDisconnected()
                }
            }
        }
    }

    private func handleMessage(_ text: String) {
        guard let dict = NetCodec.parse(text) else { return }
        let type = dict["t"] as? String ?? ""
        DispatchQueue.main.async { [weak self] in
            switch type {
            case "_bye":
                self?.listener?.onDisconnected()
            case "_hello":
                break
            default:
                self?.listener?.onMessage(dict)
            }
        }
    }

    public func send(_ json: String) {
        webSocketTask?.send(.string(json)) { error in
            if let error = error {
                print("RelayGuest send error: \(error)")
            }
        }
    }

    public func disconnect() {
        isRunning = false
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
    }
}
