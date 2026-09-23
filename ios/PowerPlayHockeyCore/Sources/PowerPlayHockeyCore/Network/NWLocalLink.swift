import Foundation
#if canImport(Network)
import Network
#endif

/// Local WiFi / LAN Host link built with Apple's modern Network.framework (`NWListener`).
/// Automatically advertises via Bonjour (`_powerplayhockey._tcp`) on TCP port 8943.
public final class NWHostLink: HostLink {
    public weak var listener: HostLinkListener?
    public let stateHz: Int = 30

    #if canImport(Network)
    private var listenerEndpoint: NWListener?
    private var activeConnection: NWConnection?
    private let queue = DispatchQueue(label: "com.powerplayhockey.hostlink")
    private var receiveBuffer = Data()
    #endif

    public init() {
        #if canImport(Network)
        startListening()
        #endif
    }

    #if canImport(Network)
    private func startListening() {
        do {
            guard let port = NWEndpoint.Port(rawValue: HOST_PORT) else { return }
            let params = NWParameters.tcp
            params.includePeerToPeer = true
            let listener = try NWListener(using: params, on: port)
            listener.service = NWListener.Service(name: "Power Play Hockey Host", type: SERVICE_TYPE)
            
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    break
                case .failed(let err):
                    print("NWListener failed: \(err)")
                    self?.stop()
                default:
                    break
                }
            }

            listener.newConnectionHandler = { [weak self] conn in
                guard let self = self else { return }
                if self.activeConnection != nil {
                    // Only 1 client per match
                    conn.cancel()
                    return
                }
                self.setupClientConnection(conn)
            }

            listener.start(queue: queue)
            self.listenerEndpoint = listener
        } catch {
            print("Failed to start NWListener: \(error)")
        }
    }

    private func setupClientConnection(_ conn: NWConnection) {
        self.activeConnection = conn
        conn.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                DispatchQueue.main.async {
                    self.listener?.onClientConnected()
                }
                self.readNext(from: conn)
            case .failed, .cancelled:
                DispatchQueue.main.async {
                    self.listener?.onClientDisconnected()
                }
                self.activeConnection = nil
            default:
                break
            }
        }
        conn.start(queue: queue)
    }

    private func readNext(from conn: NWConnection) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }
            if let data = content, !data.isEmpty {
                self.receiveBuffer.append(data)
                self.processBuffer()
            }
            if isComplete || error != nil {
                DispatchQueue.main.async {
                    self.listener?.onClientDisconnected()
                }
                self.activeConnection = nil
                return
            }
            self.readNext(from: conn)
        }
    }

    private func processBuffer() {
        while let newlineIdx = receiveBuffer.firstIndex(of: 0x0A) { // '\n'
            let lineData = receiveBuffer.subdata(in: 0..<newlineIdx)
            receiveBuffer.removeSubrange(0...newlineIdx)
            if let str = String(data: lineData, encoding: .utf8),
               let dict = NetCodec.parse(str) {
                DispatchQueue.main.async { [weak self] in
                    self?.listener?.onMessage(dict)
                }
            }
        }
    }
    #endif

    public func send(_ json: String) {
        #if canImport(Network)
        guard let conn = activeConnection else { return }
        var line = json
        if !line.hasSuffix("\n") { line += "\n" }
        if let data = line.data(using: .utf8) {
            conn.send(content: data, completion: .idempotent)
        }
        #endif
    }

    public func stop() {
        #if canImport(Network)
        activeConnection?.cancel()
        activeConnection = nil
        listenerEndpoint?.cancel()
        listenerEndpoint = nil
        #endif
    }
}

/// Local WiFi / LAN Guest link built with Apple's Network.framework (`NWConnection`).
/// Connects to a discovered host over Bonjour or directly by IP.
public final class NWGuestLink: GuestLink {
    public weak var listener: GuestLinkListener?
    public let inputHz: Int = 30

    #if canImport(Network)
    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "com.powerplayhockey.guestlink")
    private var receiveBuffer = Data()
    #endif

    public init() {}

    #if canImport(Network)
    public func connect(toHost host: String, port: UInt16 = HOST_PORT) {
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port)!)
        startConnection(endpoint)
    }

    public func connect(toEndpoint endpoint: NWEndpoint) {
        startConnection(endpoint)
    }

    private func startConnection(_ endpoint: NWEndpoint) {
        let params = NWParameters.tcp
        params.includePeerToPeer = true
        let conn = NWConnection(to: endpoint, using: params)
        self.connection = conn

        conn.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                DispatchQueue.main.async {
                    self.listener?.onConnected()
                }
                self.readNext()
            case .failed(let err):
                DispatchQueue.main.async {
                    self.listener?.onConnectFailed(reason: err.localizedDescription)
                }
            case .cancelled:
                DispatchQueue.main.async {
                    self.listener?.onDisconnected()
                }
            default:
                break
            }
        }
        conn.start(queue: queue)
    }

    private func readNext() {
        guard let conn = connection else { return }
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }
            if let data = content, !data.isEmpty {
                self.receiveBuffer.append(data)
                self.processBuffer()
            }
            if isComplete || error != nil {
                DispatchQueue.main.async {
                    self.listener?.onDisconnected()
                }
                return
            }
            self.readNext()
        }
    }

    private func processBuffer() {
        while let newlineIdx = receiveBuffer.firstIndex(of: 0x0A) { // '\n'
            let lineData = receiveBuffer.subdata(in: 0..<newlineIdx)
            receiveBuffer.removeSubrange(0...newlineIdx)
            if let str = String(data: lineData, encoding: .utf8),
               let dict = NetCodec.parse(str) {
                DispatchQueue.main.async { [weak self] in
                    self?.listener?.onMessage(dict)
                }
            }
        }
    }
    #endif

    public func send(_ json: String) {
        #if canImport(Network)
        guard let conn = connection else { return }
        var line = json
        if !line.hasSuffix("\n") { line += "\n" }
        if let data = line.data(using: .utf8) {
            conn.send(content: data, completion: .idempotent)
        }
        #endif
    }

    public func disconnect() {
        #if canImport(Network)
        connection?.cancel()
        connection = nil
        #endif
    }
}
