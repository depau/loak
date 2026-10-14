# Web target (Kotlin/Wasm)

Compile the shared Lo'ak Compose UI to the browser via Kotlin/Wasm and serve it.

## Run (development)

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Open http://localhost:8080 . The Kotlin/Wasm dev server serves the app with the
required COOP/COEP headers for wasm.

## Build (production static bundle)

```bash
./gradlew :webApp:wasmJsBrowserDistribution
```

Output: `webApp/build/dist/wasmJs/productionExecutable/` — a self-contained
static bundle (`index.html`, `webApp.js`, the app + skiko `.wasm`, `sqlite/`
runtime, `sqlite-worker.js` and `hang-watchdog.js`). Serve it with any static server that sends
`application/wasm` for `.wasm` and the COOP/COEP headers
(`Cross-Origin-Opener-Policy: same-origin`,
`Cross-Origin-Embedder-Policy: credentialless`). Keep COEP `credentialless`,
not `require-corp`: cross-origin `<audio>` streams (the subsonic `stream`
endpoint) are loaded `no-cors`, and `require-corp` makes the browser block
them unless the server sends `Cross-Origin-Resource-Policy`. `credentialless`
still provides the cross-origin isolation the sqlite wasm can use, without
breaking remote playback.

## What runs on web

- Full shared Compose UI (login, browse, playback queue, settings…).
- Playback via `WebMediaPlayerViewModel` over `HTMLAudioElement`
  (subsonic stream URLs + estimateContentLength), with MediaSession metadata.
- Room 3 (`CacheDatabase` / `DownloadDatabase`) through a custom sqlite worker
  (`wasmJsMain/resources/sqlite-worker.js`) implementing the
  `WebWorkerSQLiteDriver` message protocol.
- Crash and hang reporting to Sentry through `@sentry/browser`; see the Sentry
  section of `AGENTS.md`.

## Known web limitations (by design)

- **GIF thumbnails, kmpalette, offline downloads and the equaliser are off** on
  web (Koin-excluded / stubs). No `@JsFun`/wasm source keeps them out.
- **Room persists in OPFS** (the sqlite "SAH pool" VFS, one pool per database).
  A second tab can't open a pool another tab holds, so it falls back to an
  in-memory database for that tab.
- **No server CORS change is shipped** — like every subsonic web frontend, the
  instance must allow the origin (or you run your own proxy).

## Compose / Skiko alignment

The Kotlin/Wasm target links exactly **one** skiko. Compose core and
`material3` must therefore share a release line: the catalog pins Compose
`1.13.0-alpha01` for both, and the root build forces
`org.jetbrains.skiko:*:0.152.0-alpha02`. Mixing Compose 1.12 with material3 1.13
split skiko into two versions and broke the wasm linker/glue.
