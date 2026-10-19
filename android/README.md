# Aethera Quest Android MVP

Native Kotlin/Jetpack Compose client (Android 8.0/API 26 or newer). It logs in through AQW HTTPS, retrieves the server list, and connects directly to SmartFox over null-delimited TCP. No Python process, account credentials, session tokens, game art, or copied game assets are bundled.

## Build

Use JDK 17+ and install Android SDK Platform 35, Build Tools 34.0.0, and Platform Tools. Android Studio is optional. Set `ANDROID_HOME` or create an ignored `android/local.properties` containing `sdk.dir=<absolute SDK path>`.

From `android/` on Windows:

```text
gradlew.bat clean test assembleDebug lintDebug --no-daemon
```

The Gradle 8.9 wrapper scripts, properties, and JAR are included. The Windows wrapper forwards Gradle's exit code, including failures.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

With an authorized device connected, install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed build, not a signed production release.

## Flow and security

Login → online non-member server → map → direct connection. The password field is masked, its UI state is cleared after the login attempt, and neither credentials nor the returned login token are written to app storage.

Login and server discovery use HTTPS. HTTP cleartext is disabled by Android network security configuration. Before opening a game socket or sending the login token, the client validates the selected host against the known endpoints in `AqwConfig`: `sock7.aq.com`, `sock8.aq.com`, `euro.aqw.artix.com`, and `asia.game.artix.com`. These hosts were checked against the public HTTPS server list at `https://content.aq.com/game/api/data/servers`. If AQW adds another endpoint, verify it there before updating the allowlist; unknown hosts fail closed.

The game socket itself uses AQW's unencrypted TCP protocol, including transmission of the session token. HTTPS discovery and the host allowlist reduce redirection risk but do not encrypt or authenticate that TCP connection. Do not treat this MVP as end-to-end encrypted.

Handshake order: `verChk` → login (client version 5.1) → `firstJoin` → initial `moveToArea` → inventory request → map `tfer`. `getRmList` is optional and must not precede `firstJoin`; sending it first can leave the world handshake waiting indefinitely. The live server acknowledges entry and transfers with `%xt%server%You joined "<map>"%` and may never send sys `joinOK` or `moveToArea` — a join notice is itself proof of arrival, not a cue to wait for another area packet. Login rejection is detected from the `loginResponse` accepted flag (`%0%` with an older-version message), not only `%false%`/`logKO`. Unconsumed packets from a single read remain queued. Disconnect closes active and in-flight sockets. Superseded sessions cannot publish incoming state or contaminate the current packet framer. Handshake waits check cancellation, and cancellation closes an in-flight dial. Outbound movement uses the latest area room ID. `ViewModel.onCleared` also disconnects.

The map screen currently provides live entity tracking and schematic overview, coordinate movement, cell/pad movement, manual combat against a selected or first living monster, AFK toggle, and zone chat with an inbound chat history. Combat targets are selected from server-observed `MonMapID` values; the client never invents target IDs. Chat history is server-authoritative and bounded to the latest 200 messages.

## Headless mode

After login, server selection, and map entry, the Join screen exposes `Visual mode` and `Headless mode`:

- Visual mode includes an in-session map transfer control: enter a map such as `yulgar-1234` and press `Go map`. It sends `tfer` over the existing socket, waits for the live receive loop to observe the new map, preserves chat/connectivity after failure, and clears per-room analog/target state after success.
- Visual mode owns a normal `GameConnection` inside the ViewModel and renders the map screen.
- Visual mode includes an analog pad beside the schematic: drag in any direction to walk, hold to keep walking, release to stop, and toggle `Speed: walk` / `Speed: run`. The stick turns its vector into the next map coordinate (14% dead zone, clamped to the 0..1000 map square) and the ViewModel streams `mv` packets on a fixed tick.
- A position radar is shown beside the schematic. It projects the 0..1000 coordinate space onto a grid, displays the current position as a dot, and labels it `server-confirmed` or `estimated from your own moves`.
- AQW live packets may omit `tx`/`ty`; in that case the radar uses the last local move as an explicit estimate and shows `Position unknown` until one exists. The UI does not pretend that a local prediction is authoritative.
- Headless mode transfers the in-memory login token, selected server, and map to `HeadlessService`; it does not store the token or password on disk.
- After the service starts, the app shows a dedicated `Headless mode` screen instead of returning to login. It reports live phase, map, room, and monster count from `HeadlessRegistry`, keeps the login selection for a later retry, and offers `Stop headless`.
- A failed headless handshake shows its error on that screen rather than silently dropping back to the login form.
- `HeadlessService` is a foreground data-sync service with no Compose tree or render loop. Its low-priority ongoing notification shows connection progress, map, room, and live monster count, and includes a `Stop` action.
- Closing the Activity does not close the service (`stopWithTask=false`). Explicit Stop or a terminal connection failure closes the socket and removes the notification.
- The two modes own separate `GameConnection` instances and cannot share or race one socket. Starting headless closes any visual-side connection first.
- Android 13+ asks for notification permission before starting; refusing it cancels the headless launch rather than running invisibly.

## Verification and limits

JVM tests cover protocol serialization, UTF-8 framing, area reduction, server filtering, navigation decisions, and the real `GameConnection` handshake/lifecycle against scripted sockets. Connection regressions cover coalesced handshake packets, later room transfers, failed dial cleanup, cancellation during handshake/dial, replacement-session ownership, stale bytes from an old read, and rejection of an untrusted host before socket allocation.

Automated protocol tests use synthetic fixtures. The feature build was also installed and exercised on the user's MEmu Android 9 instance: live login/server selection, map transfer, AFK toggle, and zone-chat echo were observed. Manual combat serialization and connection ownership are covered by scripted-socket regression tests; a live monster target was not available in the tested private map, so the live combat effect remains unverified.

The map display is schematic cells/entities with HP, not a Flash gameplay renderer or a real gameplay screenshot. This Android MVP does not yet include the Python client's farming/leveling feature set or an Android foreground service for background operation.
