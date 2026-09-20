import { Room, type Env } from "./room";

/**
 * Routes /room/<code> WebSocket upgrades to the Durable Object instance for
 * that code (created lazily on first use -- there's no separate "create
 * room" step; the host picks a short random code client-side and just
 * connects). Anything else is just a liveness check for humans/monitoring.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/") {
      return new Response("Power Play Hockey relay is running.\n", { status: 200 });
    }

    const match = url.pathname.match(/^\/room\/([A-Za-z0-9]{3,12})$/);
    if (!match) {
      return new Response("Not found", { status: 404 });
    }
    const code = match[1].toUpperCase();
    const id = env.ROOMS.idFromName(code);
    const room = env.ROOMS.get(id);
    return room.fetch(request);
  },
};

export { Room };
