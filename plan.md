# Lo'ak download/library UX rework — plan & status

## Goal

Rework Lo'ak (fork of Navic, KMP Compose app) download/library UX. Consolidated feature set, derived from a multi-turn user requirements stream:

1. **Library screen**: drop the "Downloaded" FilterChip + dedicated Downloads TextButton; add a normal `Downloaded` library row (count = complete downloads) opening the Downloads screen.
2. **DownloadsScreen tabs** `[Songs, Albums, Artists, Playlists]`: list what the user explicitly downloaded (pinned, complete or not) PLUS available (on-disk cache) content. Per-song progress bar + downloaded/available indicator; collection tabs show `done/total` + % progress when < 100%.
3. **Scheduled playlist downloads**: playlists re-sync current songs + re-pin on schedule; unpin songs dropped from the playlist.
4. **Orphan unpin**: pinned songs not in any downloaded collection's current song set, not manually downloaded, not whole-library download → unpin (file stays as evictable cache, NEVER delete).
5. **Settings**: inside "Data & storage", a Downloads section — schedule picker (global default), "download now", mobile-data toggle, roaming toggle (no permission needed).
6. **Process guard**: protect user's unrelated uncommitted edits in `NowPlayingScreen.kt` / `ControlsRow.kt` + any concurrent player-UI edits.

## Current status (2026-10-07, + post-plan follow-ups 2026-10-08, + 2026-10-08 fixes)

All six requirements are **implemented**, compile green, verified on device + emulator, and **committed**. ✅ COMPLETE.

### Post-plan fixes 2026-10-08 (phantom downloads + invisible download state) — supersedes the note on line 43

After two more desktop commits landed, a device review found: (a) "a bunch of playlists I never meant to download" **were** being downloaded, and (b) from a playlist/album detail there was no way to tell if it was set for download, or its progress. Root causes + fixes (commits `4ef1ec96` + `619927f3`):

1. **Global-default auto-schedule was a real bug.** `runScheduledCollections` applied `preferenceManager.downloadScheduleCron` as a fallback to *every* plain-downloaded collection (`!scheduleEnabled && scheduleCron == null -> defaultCron`). The moment a global cron was set, every previously-downloaded playlist re-pinned + queued at schedule time. **Fix:** per-collection opt-in only — `rec.scheduleCron.takeIf { non-blank } .takeIf { scheduleEnabled }`. The global setting is now only the *prefill* in the per-playlist schedule sheet (its label/description reworded to "Default playlist sync").
2. **`setCollectionScheduleSuspend` created phantom subscription rows.** "Save Off" on a fresh playlist used to `upsert` a new PLAYLIST row. **Fix:** only upsert when `enabled`; otherwise no-op when there's no existing row.
3. **Detail screen hid real state.** `HeadingRowButtons` derived one icon from `getCollectionDownloadStatus`, which collapses to NOT_DOWNLOADED for any partial state (3/10 done + nothing active → looked like "never downloaded"). **Fix:** derive per-song status from `downloadManager.allDownloads`; show a live `x/y` progress bar when in-progress/partial, a checkmark when done, and a per-collection schedule chip (`CronSchedule.describe()`) with a re-openable `ScheduleSheet` (Download now / set / cancel).
4. Shared `CronSchedule.describe()` replaces `LibraryScreen.shortSchedule` (same output).

### Residual phantom fix 2026-10-08 (commit `ec54d2e0`) — playback cache was masquerading as downloaded playlists

The user still saw non-downloaded playlists in Downloads. The schedule fix stopped *new* phantom downloads, but the Downloads **collection tabs** unioned `store.storedCollections` into their id set — and that flow is *any collection with a complete file*, which includes **streamed playback cache**, not just deliberate downloads. Any playlist a song was merely played from showed as "downloaded" (on the debug phone: 34 streamed songs → **7** playlists listed, only **2** ever downloaded). It also kept those songs pinned via the row's `reconcileOrphans` membership.

- **Fix A (display):** Albums/Artists/Playlists tabs now show **only subscribed rows** (`DownloadCollectionEntity`); the `storedCollections` union is dropped. Songs tab keeps the on-disk union (that's its job: "everything on device"). Deterministic check on the pulled phone DB: old logic listed 7 playlists, new logic lists 0 (no collection subscriptions there).
- **Fix B (removal):** `removeCollectionRow` was dead code that forgot a row but kept pins. It now cancels/drops the collection's pending songs, deletes the row, and lets `reconcileOrphans` unpin songs not wanted elsewhere. Playlists-tab rows gain a **delete** icon (alongside refresh), so phantom/undesired rows can be removed from the Downloads screen itself (deletion previously existed only on album/artist sheets).
- Verified on emulator (`loak_test`, logged in `loaktest`): Downloads → Playlists shows exactly the one deliberately-downloaded playlist (`AI Slow and Steady`, 49/49) with refresh + delete icons; no crash (`:loak:compileAndroidMain` + `compileKotlinDesktop` green).

### Post-plan follow-ups (2026-10-08) — two desktop fixes that shipped after the main commits

- `889e544f` `fix(player): encode local file URLs for desktop playback` — playing a downloaded song on desktop built its source URL by concatenation (`"file://$localPath"`), which breaks URI parsing on spaces (e.g. "Application Support") and on literal `%40` in the store's file names (song ids are URL-encoded, `@` lands as `%40` on disk) → bogus "Couldn't reach the server" notification. Now `File.toURI()`, which percent-encodes and round-trips through `URL(...).toURI()`. Pinned by `DesktopAudioPlayerTest` (2 new tests in `loak/src/desktopTest/.../DesktopAudioPlayerTest.kt`).
- `c25a5127` `fix(desktop): implement missing ConnectivityManager.isRoaming` — the roaming gate (3cfad40a) added `isRoaming` to the expect but not the desktop actual → `NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS` broke the desktop compile. Desktop has no cellular connection, reports `false`, matching `isCellular` (`ConnectivityManager.desktop.kt`).
- `./gradlew :loak:compileAndroidMain` re-verified GREEN with both fixes in place (2026-10-08; nothing since changed).

| Area | Status |
|---|---|
| Library "Downloaded" row (5th, count = complete downloads) | ✅ implemented + verified on phone (row + count render) |
| DownloadsScreen 4 tabs + empty states | ✅ implemented + verified |
| Songs tab: per-song checkmark (done) + % + progress bar (in-progress) | ✅ verified with real downloads on emulator |
| Playlists collection tab: `done/total` + % + progress bar | ✅ verified (34/50 · 68% live, then 49/49) |
| Download a playlist end-to-end (pinned + download worker) | ✅ 49 songs persisted on disk |
| Settings Downloads group (4 rows) | ✅ implemented + verified on phone + emulator |
| Roaming toggle cycle ON↔OFF | ✅ verified (phone) |
| ScheduleSheet save round-trip | ✅ verified — `downloadScheduleCron` persisted as `0 3 * * *`, row shows desc |
| ScheduleSheet Save bug check | ✅ not a bug — early "Off" readings were missed switch taps |
| Relaunch persistence | ✅ `downloadScheduleCron` = `0 3 * * *` survives force-stop + relaunch (pref XML + UI row) |
| "Download now" (`kickAll()`) no-crash | ✅ re-verified on emulator (pid unchanged, no FATAL, UI intact) |

## Implementation notes (key decisions)

- **Pinned = explicit download intent.** `AudioFileEntity.pinned` (complete OR pending) is the truth for collection qualification; collection songs count regardless of complete status. Collection tables are attribution so tabs show "clicked download" even if nothing downloaded yet.
- **Persist attribution in DownloadDatabase** (not CacheDatabase): version 3→4, new `DownloadCollectionEntity` (collectionId PK, type ALBUM|ARTIST|PLAYLIST, scheduleCron, scheduleEnabled, createdAt, lastRunAt) + `ManualDownloadEntity` (songId PK). Authorized "deep refactor".
- **3→4 AutoMigration shipped** (`autoMigrations = [AutoMigration(from = 3, to = 4)]`) — device DBs at exactly v3 crash without it (`migrationPolicy(firstMigratedVersion=3)` only covers below-3 destructive). Verified app launches + keeps v3 download DB data.
- **Schedule loop mirrors SyncManager** (hourly poll; `nextRun(anchor)` where anchor = `lastRunAt ?: createdAt`). **Per-collection opt-in only** — the global `downloadScheduleCron` pref is the *default* prefill in each playlist's schedule sheet, and is **never** auto-applied at runtime (that superseded earlier behavior: it silently re-pinned every plain download once a global cron was set).
- **Roaming uses existing network capabilities, no new permission**: Android `NET_CAPABILITY_NOT_ROAMING`, iOS `nw_path_is_expensive`; `ConnectivityManager.isRoaming: StateFlow<Boolean>` on all 3 platforms.
- **Orphan-unpin must be collection-safe + manual-safe**: keep = current songs of each subscribed collection (resolved via DAOs) ∪ manual set ∪ library-download set ∪ caller `keep`. Path is `store.unpin()` — see Requirement-5 write-up below.
- **Collection tabs**: subscribed = `collections` flow (DownloadCollectionEntity), available = `storedSongs`/`storedCollections` (union in tabs). Not driven from `storedCollections` alone (accidental memberships).
- **kotlinx-datetime 0.8 API reality**: `atStartOfDayIn` returns `kotlin.time.Instant`; `LocalDateTime.plus(Duration)` absent. `CronSchedule.nextRun(from)` uses `date.plus(1, DateTimeUnit.DAY)` + `atStartOfDayIn` + hour/minute offset; 400-day scan guard → `Long.MAX_VALUE`.
- **`Res.string.day_N` etc. need wildcard import** `eu.depau.loak.generated.resources.*`; explicit per-name imports fail (unresolved). Same for other ScheduleSheet strings.
- **ScheduleSheet generalized** to `initialCron`/`initialEnabled` (not collection-typed) so both per-collection (Playlists tab long-press) and global-default (Settings) reuse it.
- **M3 ListItem has no `onClick` param** — click goes on modifier (`Modifier.clickable`) + `headlineContent` slot (matches SongRow pattern).

## Verification environment

- **User requested emulator instead of physical phone** → done. AVD `loak_test` (API 36 google_apis arm64 — newest image available; no API 37), booted headless, debug APK installed.
- Logged in as `loaktest` → `https://starrs.depau.eu/navidrome` (real server; 11687 songs synced). AudioMuse-AI **skipped per user** (`https://audiomuse.depau.eu` doesn't resolve / not configured).
- Physical Pixel 9 Pro still available via adb (`adb-47021FDAP007V4-bnLsO1._adb-tls-connect._tcp`) if needed.

## Remaining tasks

All done. ✅

1. **Relaunch persistence check** ✅ — force-stopped `eu.depau.loak.debug`, confirmed `downloadScheduleCron` = `0 3 * * *` in the prefs XML, relaunched, Settings → Data & storage rendered the Downloads section with no crash.
2. **"Download now" no-crash** ✅ — tapped the row on the emulator: pid stayed 7674, no `FATAL EXCEPTION` in logcat, UI intact. (`kickAll()` iterates `collectionDao.getAll()`; a stale `DownloadCollectionEntity` row is handled by `refreshCollection`'s runCatching.)
3. **Final screenshot set** ✅ captured to `/tmp/shot_downloads_songs.png`, `/tmp/shot_downloads_playlists.png`, `/tmp/shot_settings_downloads2.png` (Plus `/tmp/shot_library.png`). Playlists tab shows `49/49` complete + in-progress `1/31 · 3%` etc.; Songs tab shows per-song checkmarks.
4. **Requirement-5 write-up** ✅ present below.
5. **Commits** ✅ done — see Commit plan (3 commits, deviating from the 4-way split).
6. **Desktop follow-ups** ✅ done — see Current status; two commits (889e544f, c25a5127) landed after the status date, recorded above, `:loak:compileAndroidMain` re-verified green.

## Requirement-5 write-up (orphan unpin)

Orphan unpin calls `store.unpin()`: flips `pinned=false` and only deletes **incomplete** files on disk. Complete files stay on disk as **evictable cache** and are physically deleted ONLY by AudioStore cache eviction (over `cacheBytes`/`cacheCount` ceilings or free < `MIN_FREE_BYTES=500MB`). Therefore a song dropped from a downloaded collection is **unpinned but never physically deleted** — satisfying "file stays as evictable cache, NEVER delete". No extra code needed beyond the existing `AudioStore` eviction path.

## Commits (done — 3, not the 4-way split; see note)

1. `e0073da7 feat(downloads): track downloaded collections and manual downloads`
   → DownloadDatabase v4 + AutoMigration, DownloadCollectionEntity, ManualDownloadEntity, DownloadCollectionDao, ManualDownloadDao, DatabaseModule.kt, schema JSON `4.json`.
2. `3cfad40a feat(downloads): scheduled re-sync, orphan unpin, roaming gate`
   → DownloadManager.kt (scheduling/kick/reconcile) + ConnectivityManager (3 platforms) + CronSchedule.kt + PreferenceManager.kt.
3. `ee33f044 feat(downloads): rework Downloads screen tabs and settings`
   → LibraryScreen.kt, DataStorageScreen.kt, ScheduleSheet.kt, strings.xml, PlaylistDao.kt (`getPlaylistsByIds`).
4. `889e544f fix(player): encode local file URLs for desktop playback`
   → `MediaPlayer.desktop.kt` (`File.toURI()`), `DesktopAudioPlayerTest.kt` (2 regression tests).
5. `c25a5127 fix(desktop): implement missing ConnectivityManager.isRoaming`
   → `ConnectivityManager.desktop.kt` (1 line, matches `isCellular` = false).

**Note on the split:** the planned 4-way split wasn't clean — roaming (plan #4) and scheduled/orphan (plan #2) are interleaved in the same DownloadManager hunks, and the UI commit (plan #3) consumes *both* `overRoaming` and the schedule API. So plan #2 and #4 were collapsed into one engine commit, keeping each commit compiling standalone. Could separate with `-p` hunk splitting if a granular history is worth it later.

Each: Conventional Commit subject + body + `Co-authored-by: Codex <noreply@openai.com>`. No branches/push done (none authorized).

## Key file paths

- `loak/src/commonMain/kotlin/eu/depau/loak/data/database/DownloadDatabase.kt` (v4 + AutoMigration 3→4)
- `loak/src/commonMain/kotlin/eu/depau/loak/data/database/{DownloadCollectionDao,ManualDownloadDao}.kt`
- `loak/src/commonMain/kotlin/eu/depau/loak/data/database/entities/{DownloadCollectionEntity,ManualDownloadEntity}.kt`
- `loak/src/commonMain/kotlin/eu/depau/loak/domain/models/CronSchedule.kt` (nextRun)
- `loak/src/commonMain/kotlin/eu/depau/loak/domain/manager/DownloadManager.kt` (setCollectionSchedule, kickAll, runScheduledCollections, refreshCollection `it.song.toDomainModel()`)
- `loak/src/commonMain/kotlin/eu/depau/loak/domain/manager/PreferenceManager.kt` (downloadScheduleCron)
- `loak/src/commonMain/kotlin/eu/depau/loak/ui/screens/library/LibraryScreen.kt` (rewritten: DownloadsScreen + tabs)
- `loak/src/commonMain/kotlin/eu/depau/loak/ui/components/sheets/ScheduleSheet.kt` (generalized)
- `loak/src/commonMain/kotlin/eu/depau/loak/ui/screens/settings/DataStorageScreen.kt` (Downloads group + global schedule)
- `loak/src/commonMain/kotlin/eu/depau/loak/data/database/Migrations.kt` (migrationPolicy)

## Build & test status

- `./gradlew :loak:compileAndroidMain` ✅ GREEN
- `./gradlew :loakApp:assembleDebug` ✅ GREEN
- `:loak:compileKotlinMetadata` is a no-op; `compileKotlinIosSimulatorArm64` is EXPECTED FAIL (pre-existing Sentry Cocoa linker issue) — do NOT chase.
- Repo has NO tests; verification = compile + assemble + on-device/emulator smoke tests.
