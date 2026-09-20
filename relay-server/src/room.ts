export interface Env {
  ROOMS: DurableObjectNamespace;
}

type Role = "host" | "guest";

/**
 * One match's relay. Exactly one host and one guest WebSocket; every text
 * frame received from one is forwarded verbatim to the other. The relay
 * never parses the game's own JSON protocol (NetCodec's cfg/in/st messages)
 * -- only two frames of its own, sent to the host: `_hello` when the guest
 * joins, `_bye` when the guest (or the room) goes away. Room state is kept
 * in memory only; an idle/empty room is just garbage-collected by the
 * platform, nothing to clean up explicitly.
 */
export class Room {
  private host: WebSocket | null = null;
  private guest: WebSocket | null = null;

  async fetch(request: Request): Promise<Response> {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade?.toLowerCase() !== "websocket") {
      return new Response("Expected a WebSocket upgrade", { status: 426 });
    }

    const role = new URL(request.url).searchParams.get("role") as Role | null;
    if (role !== "host" && role !== "guest") {
      return new Response("role must be 'host' or 'guest'", { status: 400 });
    }
    if (role === "host" && this.host) {
      return new Response("Room already has a host", { status: 409 });
    }
    if (role === "guest" && this.guest) {
      return new Response("Room already has a guest", { status: 409 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    this.attach(server, role);

    return new Response(null, { status: 101, webSocket: client });
  }

  private attach(socket: WebSocket, role: Role): void {
    if (role === "host") {
      this.host = socket;
    } else {
      this.guest = socket;
      // The guest just joined -- let the host know so it can start the match.
      this.host?.send(JSON.stringify({ t: "_hello" }));
    }

    socket.addEventListener("message", (event: MessageEvent) => {
      const other = role === "host" ? this.guest : this.host;
      if (other && typeof event.data === "string") {
        other.send(event.data);
      }
    });

    const onGone = () => {
      if (role === "host") {
        this.host = null;
      } else {
        this.guest = null;
      }
      const other = role === "host" ? this.guest : this.host;
      try {
        other?.send(JSON.stringify({ t: "_bye" }));
      } catch {
        // Other side is already gone too; nothing to notify.
      }
    };
    socket.addEventListener("close", onGone);
    socket.addEventListener("error", onGone);
  }
}
