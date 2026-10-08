# AGENTS.md

This file gives an agent (or any new contributor) everything needed to navigate and work on **Lo'ak** without a human walking you through it.

## Agent workflow

- Always load the ponytail skill in ultra mode.
- For work spanning multiple conceptual changes, propose milestones with one conceptual change each. Once the user approves the plan, complete all approved milestones without stopping for confirmation between them.
- When researching a requested change, look for larger refactoring opportunities rather than applying a surgical fix as duct tape. Don't force a refactor where the small change is genuinely right, but prefer addressing the underlying structure when the change touches shared code or the pattern is already half-broken.
- Commit your own work with co-co (Conventional Commits), one commit per conceptual change, without asking for each one. Write no fixup commits: if you later find a bug or an incompleteness in one of your own earlier, still-unpushed commits, fold the correction into that original commit (e.g. `git commit --amend` or `git rebase -i`), never append a "fix what I just did" commit on top.
- Credit yourself in each commit you contribute to with `Co-authored-by: <self>`, using your model's trailer:
  - Codex/GPT → `Co-authored-by: GPT-X.X Variant <noreply@openai.com>` (e.g. `GPT-6.1 Sol`)
  - Claude → `Co-authored-by: Claude Model Version <noreply@anthropic.com>` (e.g. `Claude Opus 5.5`)
  - any other model → `Co-authored-by: Model Name and Version Including Flash if Flash <ai@depau.eu>` (e.g. `Deepseek 4-flash`, `GLM-5.3`, ...)
- **Do all code changes in your own git worktree** so concurrent agents never step on each other's working tree or uncommitted edits. Before starting, check `git worktree list` and claim a branch name no one else is using (existing convention: `feat/*`, `fix/*`):
  ```bash
  git fetch && git worktree add ../loak-<slug> -b feat/<slug>
  cd ../loak-<slug>     # work, build and commit here
  ```
  A worktree is a full checkout sharing the main repo's `.git`, so commit/amend/rebase/push behave exactly as in the main checkout. Keep the main checkout clean; don't do change work there. When your changes land, prune it: `git worktree remove ../loak-<slug>`.
- **Concurrency rules:** each agent owns exactly one worktree at a time; never edit another agent's worktree, and never work directly in the main checkout while other agents are active. Commits remain one-per-conceptual-change (co-co, co-author trailer, no fixups) — `git commit --amend` / `git rebase -i` still fold corrections. Coordinate merges with the human before pushing. Disk cost: each worktree carries its own Gradle `build/` output on top of the already-heavy build (~50 GB free disk needed), so keep worktrees few and remove them once merged.
- Ask before creating branches. Never push unless explicitly authorized.

## What is this project?

**Lo'ak** (official name, with the apostrophe) is a **fork of Navic** — a modern **(Open)Subsonic music streaming app for Android and iOS**: streaming, offline downloads/scrobbling, radio stations, lyrics (multiple providers + word-by-word), equaliser/ReplayGain/transcoding, home-screen widgets, sharing, and Android Auto support. The app was forked from Navic and released as Lo'ak; upstream contribution policies (e.g. the old "no LLM-assisted contributions" rule) do **not** apply here.

- Kotlin Multiplatform (KMP) + **Compose Multiplatform** — one shared codebase for Android + iOS UI and logic.
- Kotlin package / application id: **`eu.depau.loak`**; default branch: `master`.
- The Gradle root project is `LoakMusic` (`settings.gradle.kts`) — "Lo'ak" can't be a Gradle project name (apostrophe breaks the type-safe accessors, and "Loak" would collide with the `:loak` module's accessor).
- Current version: `v1.0.0-alpha58` (`versionCode 58`), GPL-3.0 licensed.

## Tech stack (from `gradle/libs.versions.toml`)

| Concern | Choice |
|---|---|
| Language | Kotlin `2.4.20` (very bleeding-edge — all versions here are similarly fresh) |
| UI | Compose Multiplatform `1.12.1`, Material3 `1.12.0-alpha03`, material3-adaptive `1.3.0-alpha04` (**pinned — see gotchas**; Compose held at 1.12 for the desktop window decorations) |
| Navigation | `androidx.navigation3` `1.2.0-beta01` + `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` |
| DI | Koin `4.2.2` (`koin-core`, `koin-compose`, `koin-compose-viewmodel`, `koin-android`) |
| Networking | Ktor client `3.5.2` (OkHttp on Android, Darwin on iOS), kotlinx-serialization |
| Images | Coil `3.6.2` (ktor3 network, gif) |
| Local DB | Room 3 (KMP) `androidx.room3:3.0.3` + bundled SQLite driver, KSP |
| Settings | `androidx.datastore:datastore-preferences` + `multiplatform-settings` |
| Subsonic API | `dev.zt64.subsonic:subsonic-client:1.0.0-SNAPSHOT` — **binary from a custom Maven repo** (see gotchas) |
| Icons | Valkyrie icon code-gen from SVG (see below) |
| Build | Gradle wrapper `9.7.1`, AGP `9.3.2`, JVM target 21, compileSdk 37, minSdk 24, targetSdk 37 |

## Repo layout

```
Lo'ak repo root/
├── loak/                # KMP module: ALL shared business logic + shared Compose UI
│   ├── build.gradle.kts
│   ├── schemas/         # Room schema exports (eu.depau.loak.data.database.*)
│   └── src/
│       ├── commonMain/  # shared code (the bulk of the app)
│       │   ├── kotlin/eu/depau/loak/
│       │   └── composeResources/     # font + strings.xml (per-locale dirs, Weblate managed)
│       ├── androidMain/ # Android impls (Media3/ExoPlayer, managers, permission stuff)
│       └── iosMain/     # iOS impls (AVFoundation player, UIKit managers)
├── loakApp/             # Pure Android application module (AGP), depends on :loak
│   └── src/main/kotlin/eu/depau/loak/androidApp/  # MainActivity, Application, Glance widgets
├── loakDesktop/         # Compose Desktop app module (JVM, packaged via Nucleus)
├── webApp/              # Kotlin/Wasm web build of the shared UI
├── iosApp/              # Xcode project (SwiftUI wrapper around the shared Compose UI)
│   ├── Configuration/Config.xcconfig
│   └── iosApp/          # AppDelegate.swift, Info.plist, entitlements, assets
├── gradle/libs.versions.toml          # single source of dependency truth
├── fastlane/            # iOS resign + TestFlight upload (release tooling)
├── app-repo.json        # AltStore/sideloading source manifest (auto-updated by CI)
└── .github/workflows/   # build, checks, web
```

Four Gradle modules are included (`settings.gradle.kts`): `:loak`, `:loakApp`, `:loakDesktop` and `:webApp`.

## Architecture

Classic layered single-module KMP app. Everything lives under `loak/src/commonMain/kotlin/eu/depau/loak/`:

- **`App.kt`** — root composable: Koin-injected `SessionManager`, Navigation3 back stack (sealed `Screen` keys), `SharedTransitionLayout`, theme, `Scaffold`/snackbar host, and the global `entryProvider { ... }` mapping every `Screen` to its screen composable. This is where a new screen gets registered.
- **`data/database/`** — Room 3 KMP. `CacheDatabase` (version **21**) and `DownloadDatabase` plus `dao/`, `entities/`, `mappers/`, `relations/`. `mappers/*` convert entities ↔ `domain/models/Domain*`.
- **`domain/`**
  - `manager/` — platform-abstracted managers. Key ones: `SessionManager` (owns the `SubsonicClient` + login session + caches user), `PreferenceManager` (all app settings), `DownloadManager`, `SyncManager` (starts a **periodic server sync** on startup — eager Koin singleton), `SleepTimerManager`, `ScrobbleManager`, `SnackBarManager`, etc.
  - `models/` — immutable `Domain*` dataclasses + `models/settings/`.
  - `repositories/` — bridge Subsonic API + Room DB into domain models (`*Repository` classes, constructed via `viewModel`/`singleOf`).
  - `parser/` — `LogLineParser`, `LyricsContentParser`.
- **`shared/MediaPlayer.kt`** — abstract `MediaPlayerViewModel` (queue, playback state, scrobbling, explicit-content handling); `MediaPlayer`/`MediaPlayerViewModel` are `expect`/`actual` split:
  - Android → Media3/ExoPlayer (`androidMain/.../exoplayer/`, `AndroidMediaPlayerViewModel`, `PlaybackService`, `AudioGainProcessor`).
  - iOS → AVFoundation (`iosMain/.../shared/MediaPlayer.ios.kt`, `IOSMediaPlayerViewModel`).
- **`ui/`** — Compose UI.
  - `screens/<feature>/` — each feature has `<Feature>Screen.kt` + `components/` + `viewmodels/` (Koin `viewModel {}` registered in `di/ViewModelModule.kt`).
  - `components/` — shared reusable components; `navigation/` — `Screen.kt` (sealed nav keys), `BottomSheetScene`/`NowPlayingScene` scene strategies, `PersistentViewModelStoreOwner`.
  - `theme/` — `LoakTheme` (Material3 expressive), `Colors.kt`, `Type.kt`; `util/` — transitions/helpers; `core/` — `UiState`, `PlayerUiState`.
- **`di/`** — all Koin modules; `initKoin()` composes them. `PlatformModule`/`DataStoreModule`/`rememberPlatformContext` are `expect` with Android/iOS `actual`s.
- **`util/`** — pure helper functions (`FormatUtils`, `SortUtils`, `LyricUtils`, `ReplayGainUtils`, `Logger`).

**Data flow example:** UI → ViewModel (Koin-injected Repository + Manager) → `SessionManager.api` (SubsonicClient) and/or Room DAOs → domain models → StateFlow in ViewModel → Compose.

## Code generation you must know about

1. **Valkyrie icons** — SVG sources in `loak/src/commonMain/valkyrieResources/{brand,filled,outlined}/` are compiled by the `generateValkyrieImageVector` task into `eu.depau.loak.icons.Icons.*` (generated in `build/`). All Kotlin compile tasks and KSP tasks depend on it. → **To add an icon: drop an SVG in the right `valkyrieResources` folder; never hand-write `ImageVector`s.** (The brand/app icon is `ic_loak.xml` → `Icons.Brand.Loak`.)
2. **Compose resources** — the `Res` class package is **pinned** via `compose.resources { packageOfResClass = "eu.depau.loak.generated.resources" }` in `loak/build.gradle.kts`, so imports (`eu.depau.loak.generated.resources.*`) stay stable. Don't remove that pin.
3. **Room schemas** — `room3 { schemaDirectory("$projectDir/schemas") }` auto-exports schema JSON on compile when entities change (`eu.depau.loak.data.database.CacheDatabase/…`). Currently the databases use `fallbackToDestructiveMigration(true)`, so schema bumps are low-risk. `CacheDatabase` version is currently 21.
4. **iOS TextField workaround** — `compileKotlinIos*` tasks inject a *temporary* `androidx.compose.foundation.text.input.TextFieldDecorator` into `commonMain` at build time (workaround for [KT-84055](https://youtrack.jetbrains.com/issue/KT-84055)). **Do not declare that type yourself in commonMain.**

## Build environment

- **JDK 21** (required — `jvmTarget = 21`). No other JDK version.
- **Android SDK**: compileSdk 37, Build Tools 37.0.0. If `ANDROID_HOME` is unset, create `local.properties` with `sdk.dir=/path/to/Sdk`.
- **iOS (macOS only)**: Xcode + **Apple Silicon required** — JetBrains Compose Multiplatform no longer compiles on Intel (x86_64) hosts since 1.11.1, even though Kotlin Native still supports them.
- **Resources**: the build is heavy — needs >16 GB RAM and ~50 GB free disk (`gradle.properties` sets `-Xmx8g`). Test mainly on Android; use iOS only for iOS-specific changes.

## Build & run

All Gradle commands run from the repo root with `./gradlew`.

**Android**
```bash
# Debug APK (what you'll use day to day) -> loakApp/build/outputs/apk/debug/Lo'ak.apk
./gradlew :loakApp:assembleDebug

# F-Droid flavoured build (different output name)
./gradlew :loakApp:assembleDebug -Pfdroid=true

# Rapid shared-code compile checks (no packaging) — verified task names (`./gradlew :loak:tasks --all`)
./gradlew :loak:compileKotlinMetadata   # shared-code metadata compile
./gradlew :loak:compileAndroidMain      # Android target compile
./gradlew :loak:compileKotlinIosSimulatorArm64  # iOS target compile

# Install on a connected device/emulator
./gradlew :loakApp:installDebug   # or: adb install loakApp/build/outputs/apk/debug/Lo'ak.apk

# Release APK (requires signing env vars, otherwise falls back to unsigned)
#   SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, SIGNING_STORE_PASSWORD, SIGNING_STORE_FILE
./gradlew :loakApp:packageRelease
```
The debug variant gets applicationId suffix `.debug` and label *"Loak (Dev)"*; both debug and release APKs are named `Lo'ak.apk` / `Lo'ak.fdroid.apk`.

**iOS** (macOS + Xcode)
```bash
# Build just the Kotlin framework to sanity-check iOS code
./gradlew :loak:linkDebugFrameworkIosSimulatorArm64

# Full app: open iosApp/iosApp.xcodeproj in Xcode, set your team, run the iosApp scheme
# (or replicate the CI command):
#   xcodebuild archive -project iosApp/iosApp.xcodeproj -scheme iosApp \
#     -configuration Release -sdk iphoneos -archivePath iosApp.xcarchive \
#     CODE_SIGN_ENTITLEMENTS="iosApp/entitlements.xml" CODE_SIGN_IDENTITY="-" \
#     CODE_SIGN_STYLE=Manual AD_HOC_CODE_SIGNING_ALLOWED=YES
```
`iosApp/Configuration/Config.xcconfig` derives the bundle identifier from `LOAK_DISAMBIGUATOR` (set to your `DEVELOPMENT_TEAM`), the bundle *filename* `Lo'ak.app` from `LOAK_PRODUCT_NAME`, and the home-screen/Spotlight display name `Loak` from `LOAK_DISPLAY_NAME` (apostrophe-free so Spotlight searches for "loak" match — same reason as the Android launcher label), so you can build without clobbering the real `eu.depau.loak`. The in-app brand is the shared Compose `app_name` (`Lo'ak`).

**Icons only (no full build):** `./gradlew :loak:generateValkyrieImageVector`

Debug realm: the Android `debug` build has **no ABIs excluded** (`x86_64` is added) for emulator use; release builds `arm64-v8a` + `armeabi-v7a`.

## Testing on devices and emulators

- **Never experiment on a real device and never try to control a desktop without explicit user consent.** Use an Android emulator or a VM instead, and ask permission first.
- When the user authorizes a real device, install the **development package-name variant** (the `debug` build gets the `.debug` applicationId suffix, so it installs alongside the release app).
- Use **`https://demo.navidrome.org`** with **`demo:demo`** as the test server unless instructed otherwise.
- Running an Android emulator: use `$ANDROID_HOME/emulator/emulator -avd <avd>` (if `ANDROID_HOME` is unset, the SDK is at `sdk.dir` in `local.properties`) and wait for boot with `adb wait-for-device` + `adb shell getprop sys.boot_completed` == `1`. A headless run is fine for CI-style checks; keep only one instance per AVD running.
- **An emulator launched from a sandboxed agent shell has no internet** (`ip route` shows no default route, and the app reports itself offline). Launch it outside the sandbox.
- Driving the app: `adb shell uiautomator dump` lists on-screen texts with their bounds; `adb shell input tap|swipe|text` acts on them (a long-press is a `swipe` that starts and ends at the same point, held ~900 ms); `adb shell input keyevent 4` is the system back, `111` is Esc. Compose sheets and dialogs are separate windows, so check that back reaches them (see gotcha 11).

## Sentry (error reporting)

Crash/error reporting uses the **Sentry Kotlin Multiplatform SDK** (`io.sentry:sentry-kotlin-multiplatform`, version pinned in `gradle/libs.versions.toml` via the `sentry` ref) plus the same-version `io.sentry.kotlin.multiplatform.gradle` plugin (auto-installs the SDK into `commonMain`; only `commonMain` auto-install is enabled in `:loak`).

- **Init** lives in `loak/src/commonMain/kotlin/eu/depau/loak/di/SentrySetup.kt` — `initializeSentry()` / `setSentryUser()`. It is called from the Android `Application.onCreate`, the iOS `AppDelegate.swift` (`SentrySetupKt.initializeSentry()`), and the desktop `main()`. The **DSN is a real project key** (a custom `de`-region `SentrySetup.kt` constant): reporting is live. If it ever needs toggling off (e.g. dev builds), blanking or marking the DSN breaks `Sentry.init` URI parsing — gate the call at the platform entry points instead.
- **What's reported:** `Logger.e(tag, msg, tr)` forwards non-null `Throwable`s to Sentry on all platforms (`captureSentryError` in `util/Logger.kt`); Sentry's own integrations capture unhandled crashes. The logged-in Subsonic username is attached to events via `setSentryUser` (login, logout and session restore — see `SessionManager`).
- **iOS caveat:** the Swift side compiles and the SDK init runs via the framework, but the app is built with plain `embedAndSignAppleFrameworkForXcode` (no SPM/CocoaPods) so **Sentry Cocoa must be linked into the Xcode project** for iOS crash capture to work. Wire it (add Sentry Cocoa via SPM in Xcode, then – if needed – point `sentryKmp { linker { frameworkPath.set(...) } }` at the resolved `Sentry.xcframework`), or Sentry calls become no-ops on iOS. Sentry Cocoa version must be compatible with the KMP SDK version (see the compat table in the sentry-kotlin-multiplatform README).
- **Web (Wasm)**: the KMP SDK ships a no-op stub for `wasmJs`, so `initializeSentry`/`Logger` calls compile and are inert — no web crash reporting today.

## Tests

Unit tests live in `loak/src/commonTest/` (shared logic: models, managers, navigation helpers) and `loak/src/desktopTest/` (desktop player, cache database migrations), with `kotlin("test")` as the only test dependency (`commonTest.dependencies` in `loak/build.gradle.kts`). There is no UI test suite, and **CI runs no tests** — run them yourself:

- `./gradlew :loak:desktopTest` — the quickest (JVM, runs both commonTest and desktopTest).
- `./gradlew :loak:allTests` — every target, aggregated report; also `:loak:iosSimulatorArm64Test`, `:loak:wasmJsTest`.

Add new tests to `commonTest` when the logic is shared, `desktopTest` when it needs the JVM. They don't cover UI: compile/assemble plus manual verification remain the gate for UI changes.

For UI changes: **manually verify on different themes and form factors and include a screenshot.**

## CI/CD (`.github/workflows/`)

- **`build.yml`** — on push/PR/tags: builds Android (signed release on push/tags via `SIGNING_*` env vars, debug on PRs, plus a `.nightly` variant on `main`), desktop installers for Ubuntu/macOS/Windows, and (disabled) an iOS IPA. Publishes a `nightly` GitHub Release on every `main` push and a v-tag GitHub Release with desktop update manifests.
- **`checks.yml`** — Gradle wrapper integrity validation.
- **`web.yml`** — builds the Kotlin/Wasm web bundle and deploys the web demo.

Nothing in CI runs tests today.

## Conventions & rules

- **Formatting** (`.editorconfig`): tabs, indent 4 (`indent_style=tab`, `indent_size=4`), max line length 100, LF. YAML/Ruby use 2-space spaces.
- **Commits**: [Conventional Commits](https://conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `refactor:`, ...) — matches existing history.
- Branch naming in use: `feat/*`, `fix/*`, `night`, `dev`.
- **Strings**: user-facing text goes in `loak/src/commonMain/composeResources/values/strings.xml`; translations are managed on Weblate — don't hand-edit the `values-*` locale dirs.
- **List items behave the same everywhere.** A row for the same kind of thing (song, album, artist, playlist…) responds to the same gestures in every list: if a song row opens the song sheet on long-press in one place, it does so in every list that shows songs (the queue, its Autoplay suggestions, search, collections…), and likewise for tap, swipe and drag. When adding a list, or a gesture to one, match the others; omit only actions that make no sense there (e.g. no "Remove from queue" for a song not in the queue).
- **Keep PRs small and focused**; screenshots required for UI changes.
- This is a **self-maintained fork**: upstream's old "no LLM-assisted contributions" rule does not apply.

## Gotchas / things agents trip on

1. **Version catalog is intentionally pinned** in places with comments — e.g. `material3-adaptive` must stay on `1.3.0-alpha04` (alpha05 makes the detail pane always visible). Do **not** blindly bump dependencies; read the `#noinspection`/comment lines in `gradle/libs.versions.toml`. There is also a global exclude that strips Material 2 — don't reintroduce it.
2. **`subsonic-kotlin` is a SNAPSHOT from a private Maven repo** (`https://raw.githubusercontent.com/Nightdavisao/maven-repo/...`) with a 1-hour SNAPSHOT cache. Don't be surprised by flaky freshening; don't "fix" it by pinning a release that doesn't exist.
3. **Do not declare** `androidx.compose.foundation.text.input.TextFieldDecorator` in commonMain (shares the inject-on-build workaround).
4. **Nightly/beta-quality toolchain.** Kotlin 2.4.20, AGP 9.3.2, Compose 1.12.0 are pre-release — a compile error may be a toolchain issue; search for known issues before "fixing" shared code.
5. `viewModelModule` wires ViewModel constructor deps manually (some take screen params by factory lambda). New ViewModels must be registered there, not `singleOf`'d.
6. New managers/repositories that need platform impls follow the `expect`/`actual` pattern (see `PlatformModule`, `DataStoreModule`, `ConnectivityManager`, `ShareManager`). Remember to add the Koin registration in **both** platform `platformModule`s.
7. Exported names/`-Xexpect-actual-classes` and `-Xexplicit-backing-fields` compiler flags are set globally; note the `field = ...` property syntax used in ViewModels/flows (backing-field feature).
8. Building Android requires networked dependency resolution on first run (long). Use `--offline` only after a successful full build.
9. **Fork placeholders:** a couple of spots still carry upstream-era placeholders (`.github/README.md` update links). Point these at the fork's own hosting when they matter. This is a **self-maintained fork** — the upstream "no LLM-assisted contributions" rule does not apply here.
10. **App name: `Lo'ak` is official; `Loak` is only for search labels.** The official name is **Lo'ak** (with the apostrophe) everywhere in-app and in the repo. But labels used for *searching* the app — e.g. the Android launcher name — should be **`Loak`** (apostrophe-free) when the search engine doesn't handle fuzzy/quoted matching or when there's any concern a user typing "loak" wouldn't find the app. On **Android** the launcher/recents label is `@string/app_name` (`loakApp/src/main/res/values/strings.xml` → `Loak`, plus the debug `resValue` `"Loak (Dev)"`); on **iOS** it's `LOAK_DISPLAY_NAME=Loak` in `Config.xcconfig` → `INFOPLIST_KEY_CFBundleDisplayName` (the internal bundle filename stays `Lo'ak.app` via `LOAK_PRODUCT_NAME`). The in-app brand (shared Compose res `app_name` used in the UI) stays `Lo'ak` on both. Also note: an *unescaped* `'` in an aapt-compiled Android string makes build-tools 37 aapt2 fail with a baffling `Invalid unicode escape sequence in string "{str}"` on `app_name` — if you ever reintroduce `Lo'ak` in an Android res string, escape it as `Lo\'ak` (renders identical). Compose-resource strings (`composeResources/values/strings.xml`) don't go through aapt (they're assets), so they don't need escaping.

11. **Never provide `LocalNavigationEventDispatcherOwner` over the platform's.** Dialogs (`ModalBottomSheet`, `Dialog`) inherit composition locals from the screen that shows them, so their back handlers would register with the provided dispatcher, while Android delivers back to the dialog's own window: back and the back gesture then silently do nothing on every open sheet. `App.kt` feeds keyboard/mouse back into the platform's dispatcher and only provides one of its own where the platform has none.

## Quick orientation questions

- *Where do I add a new screen?* → create `ui/screens/<feature>/…`, add a `Screen` variant in `ui/navigation/Screen.kt`, then register it in the `entryProvider` in `App.kt` (+ ViewModel in `di/ViewModelModule.kt`).
- *Where do I add a server API call?* → `domain/repositories/*Repository.kt` via `sessionManager.api` (SubsonicClient), or a new repository wired in `di/RepositoryModule.kt`.
- *Where do I add a setting?* → `domain/manager/PreferenceManager.kt` + settings UI under `ui/screens/settings/`.
- *Building for iOS?* → macOS + Apple Silicon + Xcode required; see the iOS build section.
