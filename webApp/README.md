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
runtime and `sqlite-worker.js`). Serve it with any static server that sends
`application/wasm` for `.wasm` and the COOP/COEP headers
(`Cross-Origin-Opener-Policy: same-origin`,
`Cross-Origin-Embedder-Policy: require-corp`), necessary for the sqlite worker.

## What runs on web

- Full shared Compose UI (login, browse, playback queue, settings…).
- Playback via `WebMediaPlayerViewModel` over `HTMLAudioElement`
  (subsonic stream URLs + estimateContentLength), with MediaSession metadata.
- Room 3 (`CacheDatabase` / `DownloadDatabase`) through a custom sqlite worker
  (`wasmJsMain/resources/sqlite-worker.js`) implementing the
  `WebWorkerSQLiteDriver` message protocol.

## Known web limitations (by design)

- **GIF thumbnails, kmpalette, offline downloads and the equaliser are off** on
  web (Koin-excluded / stubs). No `@JsFun`/wasm source keeps them out.
- **Room is in-memory on web** — databases won't persist across reloads. The
  OPFS path blocks the worker on `BEGIN EXCLUSIVE` (needs the opfs-async-proxy
  wiring); plain `oo1.DB` keeps the driver working. Room's "reader connection"
  timeouts can appear during boot while the initial schema is created — they
  are recoverable.
- **No server CORS change is shipped** — like every subsonic web frontend, the
  instance must allow the origin (or you run your own proxy).

## Compose / Skiko alignment

The Kotlin/Wasm target links exactly **one** skiko. Compose core and
`material3` must therefore share a release line: the catalog pins Compose
`1.13.0-alpha01` for both, and the root build forces
`org.jetbrains.skiko:*:0.152.0-alpha02`. Mixing Compose 1.12 with material3 1.13
split skiko into two versions and broke the wasm linker/glue.
