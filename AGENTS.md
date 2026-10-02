# AGENTS.md

This file gives an agent (or any new contributor) everything needed to navigate and work on **Lo'ak** without a human walking you through it.

## What is this project?

Lo'ak is a **fork of Navic** — a modern **(Open)Subsonic music streaming app for Android and iOS**: streaming, offline downloads/scrobbling, radio stations, lyrics (multiple providers + word-by-word), equaliser/ReplayGain/transcoding, home-screen widgets, sharing, and Android Auto support. The original upstream project was renamed to Lo'ak for this fork; upstream contribution policies (e.g. the old "no LLM-assisted contributions" rule) do **not** apply here.

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
Navic repo root (renamed "Lo'ak")/
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
├── iosApp/              # Xcode project (SwiftUI wrapper around the shared Compose UI)
│   ├── Configuration/Config.xcconfig
│   └── iosApp/          # AppDelegate.swift, Info.plist, entitlements, assets
├── gradle/libs.versions.toml          # single source of dependency truth
├── fastlane/            # iOS resign + TestFlight upload (release tooling)
├── app-repo.json        # AltStore/sideloading source manifest (auto-updated by CI)
└── .github/workflows/   # build, checks, mirror, publish
```

Only two Gradle modules are included (`settings.gradle.kts`): `:loak` and `:loakApp`.

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
3. **`BuildInfo.kt`** — `generateBuildInfo` task writes an (empty) `eu.depau.loak.generated.BuildInfo`, kept as a compile target; no flags today.
4. **Room schemas** — `room3 { schemaDirectory("$projectDir/schemas") }` auto-exports schema JSON on compile when entities change (`eu.depau.loak.data.database.CacheDatabase/…`). Currently the databases use `fallbackToDestructiveMigration(true)`, so schema bumps are low-risk. `CacheDatabase` version is currently 21.
5. **iOS TextField workaround** — `compileKotlinIos*` tasks inject a *temporary* `androidx.compose.foundation.text.input.TextFieldDecorator` into `commonMain` at build time (workaround for [KT-84055](https://youtrack.jetbrains.com/issue/KT-84055)). **Do not declare that type yourself in commonMain.**

## Build environment

- **JDK 21** (required — `jvmTarget = 21`). No other JDK version.
- **Android SDK**: compileSdk 37, Build Tools 37.0.0. If `ANDROID_HOME` is unset, create `local.properties` with `sdk.dir=/path/to/Sdk` (on this dev machine: `/home/depau/Android/Sdk`).
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

**Android emulator (this dev machine)** — verified working:
```bash
export ANDROID_HOME=/home/depau/Android/Sdk QT_QPA_PLATFORM=xcb
/home/depau/Android/Sdk/emulator/emulator -avd Pixel_9_Pro -gpu swiftshader_indirect -no-snapshot-save -no-boot-anim
```
- Installed AVDs: `BRACCIOv7`, `Pixel_9_Pro`, `Pixel_Fold_API_35_Android_15_`, `Wear_OS_Small_Round` (there is **no** plain "Pixel 9"; the Pixel 9 Pro is the closest).
- `-gpu swiftshader_indirect` is required: host GPU can't be used (Vulkan init fails), so the emulator falls back to software rendering. Without it you get a fatal `Qt platform plugin "wayland"` error → the visible window needs `QT_QPA_PLATFORM=xcb`; plain X11 (`:0`) is available via xcb.
- Headless alternative: add `-no-window` (still use swiftshader) — usable via `adb` for CI-style runs.
- Only **one** running instance per AVD: launching the same AVD twice fatals with "Running multiple emulators with the same AVD… use -read-only".
- Boot check: `adb wait-for-device` then poll `adb shell getprop sys.boot_completed` for `1`. Stock window is 1280x2856 @480dpi.

## Tests

> **Important: this repository currently has NO tests.** There is no `commonTest`/`androidUnitTest`/`iosTest` source set, no test dependencies in `gradle/libs.versions.toml`, and no CI test job. Do not claim "tests pass" — the closest sanity gates are compile/assemble tasks above and manual UI verification.

**If you add tests** (KMP style, for `:loak`):
1. Create `loak/src/commonTest/kotlin/...` (or `androidUnitTest`/`iosTest`).
2. Add a dependency in `loak/build.gradle.kts`'s `sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }`.
3. Run them with the KMP test tasks — confirmed: `./gradlew :loak:allTests` (aggregate), `:loak:iosSimulatorArm64Test`, and target-specific `*Test` tasks (list with `./gradlew :loak:tasks --all`).

For UI changes: **manually verify on different themes and form factors and include a screenshot.**

## CI/CD (`.github/workflows/`)

- **`build.yml`** — on push/PR/tags: builds Android (debug on PR, signed release on push) and an iOS IPA (`macos-26`), posts APKs to a Discord webhook, and on `v*` tags creates a GitHub Release with APK+IPA.
- **`checks.yml`** — Gradle wrapper integrity validation.
- **`mirror.yml`** — mirrors the repo to Codeberg.
- **`publish.yml`** — on GitHub Release: TestFlight upload via `fastlane` and the AltStore manifest (`app-repo.json`) update.

Nothing in CI runs tests today.

## Conventions & rules

- **Formatting** (`.editorconfig`): tabs, indent 4 (`indent_style=tab`, `indent_size=4`), max line length 100, LF. YAML/Ruby use 2-space spaces.
- **Commits**: [Conventional Commits](https://conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `refactor:`, ...) — matches existing history.
- Branch naming in use: `feat/*`, `fix/*`, `night`, `dev`.
- **Strings**: user-facing text goes in `loak/src/commonMain/composeResources/values/strings.xml`; translations are managed on Weblate — don't hand-edit the `values-*` locale dirs.
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
9. **Fork-specific:** a handful of places still point at the upstream repo as placeholders (`.github/README.md`, `app-repo.json` URLs, `ChangelogSheet.kt`/`AboutScreen.kt` update-check URLs, `mirror.yml`, `publish.yml` secrets like `LOAK_GITHUB_TOKEN`). Point these at the fork's own repo/hosting when it's created.
10. **Searchable app-launcher names are deliberately apostrophe-free — and apostrophes break aapt2.** The brand is `Lo'ak`, but Android launcher search and iOS Spotlight/App Library both do case-insensitive substring matching on the installed app's label, so `Lo'ak` would never match a search for `Loak`. On **Android** the launcher/recents label is `@string/app_name` (`loakApp/src/main/res/values/strings.xml` → `Loak`, plus the debug `resValue` `"Loak (Dev)"`); on **iOS** it's `LOAK_DISPLAY_NAME=Loak` in `Config.xcconfig` → `INFOPLIST_KEY_CFBundleDisplayName` (the internal bundle filename stays `Lo'ak.app` via `LOAK_PRODUCT_NAME`). The in-app brand (shared Compose res `app_name` used in the UI) stays `Lo'ak` on both. Also note: an *unescaped* `'` in an aapt-compiled Android string makes build-tools 37 aapt2 fail with a baffling `Invalid unicode escape sequence in string "{str}"` on `app_name` — if you ever reintroduce `Lo'ak` in an Android res string, escape it as `Lo\'ak` (renders identical). Compose-resource strings (`composeResources/values/strings.xml`) don't go through aapt (they're assets), so they don't need escaping.

## Quick orientation questions

- *Where do I add a new screen?* → create `ui/screens/<feature>/…`, add a `Screen` variant in `ui/navigation/Screen.kt`, then register it in the `entryProvider` in `App.kt` (+ ViewModel in `di/ViewModelModule.kt`).
- *Where do I add a server API call?* → `domain/repositories/*Repository.kt` via `sessionManager.api` (SubsonicClient), or a new repository wired in `di/RepositoryModule.kt`.
- *Where do I add a setting?* → `domain/manager/PreferenceManager.kt` + settings UI under `ui/screens/settings/`.
- *Building for iOS?* → macOS + Apple Silicon + Xcode required; see the iOS build section.
