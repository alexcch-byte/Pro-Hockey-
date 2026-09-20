# Power Play Hockey — internet relay

A tiny Cloudflare Worker that lets two tablets play a match over the open
internet instead of a local WiFi network or Bluetooth. It's a dumb relay: a
Durable Object per room (keyed by a 5-character code) pairs up a host and a
guest WebSocket and pipes whatever either side sends straight to the other.
It never parses the game's own protocol (`NetCodec`'s `cfg`/`in`/`st`
messages) — only two small control frames of its own, `_hello` (sent to the
host when the guest joins) and `_bye` (sent to whichever side is left when
the other disconnects).

On the Android side, `RelayHost`/`RelayGuest`
(`app/src/main/java/com/tablehockey/game/network/RelayLink.kt`) implement
the same `HostLink`/`GuestLink` interfaces `GameServer`/`GameClient` (WiFi)
and `BluetoothHost`/`BluetoothGuest` do, so `GameActivity`, `GameView` and
`NetCodec` needed no changes at all — this is exactly the extension point
that interface split was built for.

## Deploying your own relay (one-time)

This needs your own free Cloudflare account — there's no way around that
step, it can't be done on your behalf here.

1. **Sign up** at https://dash.cloudflare.com/sign-up if you don't already
   have an account (no credit card required for this).
2. From this directory, install dependencies and log in:
   ```bash
   npm install
   npx wrangler login
   ```
   `wrangler login` opens a browser to authorize the CLI against your
   account.
3. Deploy:
   ```bash
   npx wrangler deploy
   ```
   This prints a URL at the end, something like:
   ```
   https://powerplay-hockey-relay.<your-subdomain>.workers.dev
   ```
4. Paste that URL into `RELAY_BASE_URL` in
   `app/src/main/java/com/tablehockey/game/network/NetMessage.kt` (it ships
   with a placeholder value that won't work), then rebuild the app:
   ```bash
   ./gradlew assembleDebug
   ```

That's it — no ongoing maintenance. Cloudflare's free tier for Workers +
Durable Objects is generous enough that a hobby game's traffic won't come
close to it, and there's no server to patch or keep running; it only runs
when a room is active.

## Note on this environment

`npm install` may fail with `EPERM`/`EBADF` errors if this folder is synced
by Google Drive (the project lives at `G:\My Drive\...`) — Drive's sync
client can lock files mid-write during `node_modules`' churn. If that
happens, either pause Drive sync while installing, or run `npm install`
from a copy of this folder outside the synced path and copy `node_modules`
back (or just run `npx wrangler deploy` from that unsynced copy directly,
since only the deployed Worker matters, not where you ran the CLI from).

## Testing changes locally before deploying

```bash
npx wrangler dev
```

Runs the Worker locally (Miniflare) with a `ws://localhost:8787` endpoint
you can point a debug build at temporarily.

## Room codes

Codes are 5 characters from an alphabet that skips visually-confusable
characters (no `0`/`O`, `1`/`I`/`L`) and are generated client-side by
`RelayHost` when hosting starts — there's no separate "create room" call.
The Durable Object for a code is created lazily on first connection, so an
unused code costs nothing and nothing needs cleaning up afterward; an idle
room's Durable Object is simply garbage-collected by the platform.
