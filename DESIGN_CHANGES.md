# Design changes

Changes made on the design canvas (https://claude.ai/artifact/8hyPuTmghpvYy3ut5QnaBi)
that the app still has to catch up with. Items marked **[done]** are implemented.

## Search

- **[done]** **Playlists search target** and new chip order: All / Songs / Playlists /
  Artists / Albums (result sections follow the same order). The chip row no
  longer fits the width, so it scrolls horizontally, the last chip cut at the
  edge. Boards: Search, Search results.
- **[done]** **Recent searches** fill the empty search page (already existed in
  the app; the reference screenshot just had no history yet): history rows with a remove
  button each, and a "Clear" link. Board: Search.

## Dialogs

- **[done]** **Material 3 dialogs**: actions are end-aligned text buttons ("Cancel" /
  "OK", "Cancel" / "Create") instead of stacked full-width filled buttons.
  Boards: Create playlist, Choice dialog.
- ~~One scrim for dialogs and sheets~~: dropped. The 60% dim is Android's own
  dialog default; overriding it isn't worth custom window flags.
- **[done]** **Theme mode** is an inline segmented button (System / Light / Dark) on the
  theme page instead of a dialog. Board: Theme.
- **(low priority, not done)** **Artwork and artist image shape** are inline pickers (four tappable
  previews that apply immediately) instead of dialogs. The shape dialog is
  gone. Board: Appearance.

## Sheets

- **[done]** **Drag handle** on every bottom sheet (the album/song/player option sheets
  had none).
- **[done]** **Option sheet order**: common actions first (Play next, Add to queue, Add to
  playlist…), external links (last.fm, MusicBrainz) last, after a divider.
  Boards: Album options, Song options (both).
- **[done]** **Playback speed** sheet gets a title. (Range labels skipped: the
  preset chips already show the range.)

## Mini player

- **[done]** **Star instead of Next**: the mini player's second button is Star; skipping
  is already a swipe on the mini player. All phone boards.

## Navigation

- **[done]** **Settings moves into the Account sheet** (first row); the gear icon is
  removed from the tab top bars. Boards: Library, Albums, Playlists, Artists,
  Account.
- **[done]** **Nav bar keeps the originating tab selected** on pushed screens (album →
  Albums, genre/songs/starred/search/shares → Library, …).

## Queue

- **[done]** **Queue rows behave like library song rows**: long-press opens the song
  options sheet (queue variant: Play next, Remove from queue, Add to playlist,
  Star, Download, View album, View artist, Share, View track info) and the swipe
  actions work (right: add to end of queue, left: play next, both as moves per
  the rule above). Today a long-press just plays the song. Note on the Queue
  board.

## Lists and details

- **[done]** **Artists fast-scroll index**: the existing "Alphabetical scrolling"
  setting is on by default. Board: Artists.
- **Artists letter gutter** (open): the mockup puts each letter in a gutter
  instead of a header row. The app's header rows are *sticky* (the current
  letter stays pinned while scrolling), which the gutter would lose. Kept as is
  until decided.
- **[done]** **Genre cards**: names stay clear of the art and wrap on word boundaries only.
  Boards: Genres, Library.
- **[done]** **Starred empty state** uses a star icon. (Hint line skipped.)
  Board: Starred.
- ~~Play button colour~~: no change. It already follows the cover colours via
  Appearance → Dynamic theming; the near-black comes from a grey cover.

## Playlists

- **[done]** **Quick add**: "Add to playlist" in an options sheet saves straight to the
  last-used playlist and shows a snackbar "Saved to X · Change"; "Change" opens
  the save-to-playlist sheet. With no last-used playlist it opens the sheet
  directly (YouTube Music-style). Board: Quick add.
- **[done]** **Save-to-playlist sheet** replaces the checkbox dialog: filter field,
  "New playlist" row, then playlists most recently used first; tapping one saves
  and closes, no OK/Cancel. Board: Save to playlist.
- **[done]** **Inline create**: "New playlist" turns into a name field + Create button in
  the sheet; the song goes into the new playlist. Replaces the Create playlist
  dialog in this flow. Board: Save to playlist (new).
- **[done]** **Edit playlist** (new): name, description, public switch, in a sheet. Reached
  from "Edit playlist" in the playlist options and overflow menu.
  Boards: Edit playlist, Playlist options.
- **[done]** **Duplicate check on save**: before saving, check only the target playlist
  (one request). If the song is already there, nothing is added and a snackbar
  says "Already in X · Add anyway". Board: Already in playlist.
- **[done]** Read-only playlists (shared by other users) are not offered as
  targets: adding to them fails on the server.
- Not doing: marking every playlist in the sheet that contains the song (likely
  expensive).

### AudioMuse-AI and smart playlists (proposal, not done)

- **AudioMuse-AI awareness** (setting, on by default, in Server › Server features).
  Name patterns: `_automatic` with an optional ` (n)` chunk number (regex
  `_automatic(\s*\(\d+\))?$`; deleted and recreated on every clustering run),
  `… by AudioMuse-AI` (scheduled: Sonic Fingerprint, Album of the Week; emptied
  and refilled in place) and `_instant` (made on request, never touched again).
  The suffix is hidden everywhere: lists, Home, speed dial, search, sheets,
  snackbars, the startup playlist picker.
- **Badges** go before the song count:
  - **AI**, filled, with a sparkle: playlists AudioMuse-AI rebuilds.
  - **AI**, outlined: `_instant` playlists.
  - **Smart**, with a soundwave: Navidrome smart playlists.

  Speed dial tiles get a round corner badge, top right; the pin stays top left.
- **Sonic Fingerprint and Album of the Week** get gradient covers with their name
  on them (like YT Music's My Supermix), and come first on Home's Made for you
  shelf (overline "From AudioMuse-AI").
- **Alchemy radios** (scheduled from Song Alchemy, refilled in place) carry the
  filled AI badge, the subtitle "Radio · N songs", and sit after those two on
  Made for you. Their names have no marker (AudioMuse-AI uses the anchor name), so
  they are only spotted when an **AudioMuse-AI server** is set (optional; Server ›
  Server features; URL + optional API token). Lo'ak reads `GET /api/radios`
  (Bearer `API_TOKEN`) and matches enabled radios by exact name.
- **Badge explanation**: tapping a badge on the playlist page opens a rich tooltip
  (title, one-line why, actions: Keep a copy / Edit rules / Got it). Copy per badge
  is on the Details page note. Board: Playlist — badge info.
- **Soft read-only** (rebuilt playlists only): a notice on the detail page and a
  warning in Edit playlist, both with **Keep a copy** (a regular playlist with the
  clean name). They go into a bottom section of Save to playlist with "songs you
  add here may disappear"; `_instant` ones stay in the main list. Edit keeps the
  suffix when saving.
- **Smart playlists**: detected by the Subsonic `validUntil` field (works on any
  server that sets it). They are read-only: no remove, never offered as a save
  target. The detail page shows a rules summary card and "updated N min ago".
- **Smart playlist editor** (Navidrome 0.62+ only, through `/api/playlist` with a
  JWT from `/auth/login` using the stored password): match all/any, rule cards,
  nested groups, sort/order/limit, public. The create button turns into a menu
  (Playlist / Smart playlist). A field picker has common fields plus "Other tag…".
  Playlists imported from `.nsp` files are view-only (the next scan would
  overwrite edits).
- Boards: Home, Home (tablet, medium), Playlists, Playlist, Smart playlist,
  New playlist menu, Playlist options, Edit playlist, Save to playlist (+ new),
  Quick add, Already in playlist, Delete playlist, Remove from playlist, Create
  playlist, Startup playlist, Search (tablet), Server, Smart playlist editor,
  Rule field.
- Fixes this would bring along: Home's Made for you shelf hands the stripped name
  to Edit playlist, so saving it renames the playlist and drops the suffix. The
  last-used quick-add path skips the `readOnly` check.

## Do it now, offer undo

- **[done]** Destructive and list actions happen immediately with an "Undo" snackbar
  instead of a confirmation dialog: delete playlist, remove from playlist,
  clear queue.
  Boards: Delete playlist, Remove from playlist, Clear queue.
- **[done]** **Queue duplicates are moved, not duplicated**: "Play next" or "Add to
  queue" on a song that's already in the queue moves it to the requested spot
  (right after the current song, or to the end) and removes it from where it
  was. Snackbar "Moved to play next · Undo" / "Moved to end of queue · Undo";
  Undo puts it back. Replaces the "already in the queue" dialog and its "Don't
  show again" preference. Board: Moved in queue.

## Notification

- **[done]** **Star is the only extra button** in the media notification (after
  Previous / Next), like the mini player. Filled when starred; tapping toggles
  it. Shuffle and Repeat are dropped from the notification and stay on the Now
  playing screen. Board: Media notification.
- **Small icon** is still Navic's glyph; it gets the new Lo'ak mark once the
  branding is done.

## Settings

- **[done]** **Default grid size is 3×3** (3 columns in every art grid: albums, genres,
  playlist grid, search). **(low priority)** The grid size setting becomes an
  inline picker (2×2 / 3×3 / 4×4) instead of a dialog. Boards: Albums, Albums (pushed),
  Genres, Appearance.
- **[done]** **Equaliser** labels in dB (±15 dB) with each band's frequency under its
  slider and the current value shown. Board: Equaliser.

## Queue sync

- **[done]** **Queue sync section** in Settings → Playback: "Sync queue across devices"
  switch (on by default) and "On startup, play": Latest queue from server
  (default; this device's queue when offline), Latest queue on this device, A
  playlist (reloaded from the server at every start), Nothing. Boards: Playback,
  On startup, play, Startup playlist.
- **[done]** **Pull only on startup.** A remote player never replaces the queue while the
  app runs, so multi-device playback keeps working.
- **[done]** **Push** when playback starts, pauses or stops, on every track change,
  and every 30 s while playing (the position for resuming elsewhere).
- **[done]** **Picked-up queue**: the mini player shows "From Desktop" in place of the
  artist, and the queue header shows "From Desktop · 2 min ago". Both go away
  once this device pushes the queue. A queue saved as `Lo'ak (X)` shows
  "From X"; any other client name is shown as is ("From Feishin"). Boards:
  Queue actions (the Library, queue picked up board was dropped with the Library page).
- **[done]** **Queue header controls** (phone queue, tablet queue pane, Up next tab of the
  tablet player): one ⋯ menu, no dedicated sync button. Items: server queue
  status, Send this queue to the server, Load the server queue (with undo);
  then, in one section below, Save to playlist… and Clear queue (with undo). This replaces the "Clear
  queue" chip. Boards: Queue, Queue actions, Queue pane, Now playing (tablet).
- **[done]** **Server settings page**: server address, username, password ("Save and
  reconnect"), custom server headers, and Device name (sent as the Subsonic
  client name `Lo'ak (<name>)`; empty means the system name). The server address
  in the Account sheet opens it, and it is the first row in Settings. Boards:
  Server, Device name, Settings, Account.
- **Client library note**: `dev.zt64.subsonic` 1.0.0-SNAPSHOT can't save a
  queue: `savePlayQueue` takes one song id, one overload calls the wrong
  endpoint, and `getPlayQueueByIndex` returns nothing. Unless a library update
  fixes this, Lo'ak calls `getPlayQueue[ByIndex]` / `savePlayQueue[ByIndex]`
  itself through `SubsonicClient.httpClient`.
- **Differences from the mockups** (app as built, feat/queue-sync):
  - The startup playlist is picked from a plain choice dialog, not the Save to
    playlist sheet; the choice dialogs now scroll for long lists.
  - The Settings "Server" row has a fixed subtitle ("Address, account, this
    device's name") instead of "user • host".
  - The whole account card in the Account sheet opens Server (bigger target
    than the address link).
  - Choice dialog options have no subtitles (the existing dialog has none).
  - Server queue songs the library sync hasn't seen yet are dropped.

## Newly mapped (already in the new style)

These were added to the canvas after the first mapping. They show the current
app's content with the conventions above applied (M3 text-button dialogs, 32%
scrim, drag handles, common actions first); each has a reference screenshot of
the current app, except the two reconstructed from code.

- Playlist options, Song options (in a playlist), Artist options sheets.
- Create share dialog.
- "Leaving Lo'ak" (external link) dialog, from code.
- Add to playlist, Delete playlist and "already in the queue" were mapped too,
  then redesigned (see Playlists and Do it now, offer undo).
- Swipe actions on song rows (right: add to queue, left: play next).

## Behaviour before these changes (tested 2026-09-27)

- **Queue duplicates**: detected. Adding a song that's already queued (Play next
  or Add to queue) asks "This song is already in the queue. Do you want to add
  it again?" with "Don't show again". The design turns this into an "added
  again · Undo" snackbar.
- **Playlist duplicates**: NOT detected. Adding the same song to the same
  playlist twice silently stores it twice (the server accepts it); the snackbar
  just says "Added to X." both times. The picker also keeps the previous
  selection ticked, which looks like "already in this playlist" but isn't.
- **Remove from playlist**: already immediate, snackbar "Removed from
  playlist." with no Undo. The design adds Undo.

## Tablet & desktop

Page "Tablet & desktop" on the canvas. Mapped from the Pixel Tablet emulator
(1280×800 dp landscape, 800×1280 dp portrait). The app ships to Android, iOS,
desktop and web, so the layout follows the Material 3 window size classes
instead of a tablet special case.

- **[done]** **Compact (< 600 dp)**: the phone design, unchanged.
- **[done]** **Medium (600–839 dp)**: a navigation rail replaces the nav bar; the
  floating mini player stays, over the content only. Board: Library · medium.
- **[done]** **Expanded (≥ 840 dp)**: rail plus a **floating player bar** with the full
  controls (song, star, transport, time, lyrics, queue, open player), aligned
  with the content column; content scrolls under it. Chosen over a docked bar.
  Board: Library.
- **[done]** **Account** moves to the bottom of the rail (Settings stays in its
  sheet). **(not done)** The search *field* in the top bar: the top bar keeps
  its search icon for now.
- **[done]** **Details open in the content area**, not in a list-detail split: today the
  root tab gets squeezed into a 384 dp list pane, nav bar and mini player
  included. Album/playlist detail on expanded: cover and actions in a left
  column, tracks on the right; buttons keep their natural width. Settings
  keeps its list-detail. Boards: Album, Settings.
- **[done]** **Grids** use adaptive columns on wider windows (already the case:
  the grid-size setting only applies to compact). Library shortcuts share one
  row on expanded windows. Board: Albums.
- **[done]** **Artist**: same structure as the phone (bio decision still pending, see
  To consider later), but the photo sits in a rounded card inset from the rail
  (a full-width fade looked cut off against the rail), with a scrim under the
  name and bio; normal-width buttons, albums as a grid.
  Board: Artist.
- **[done]** **Now playing (expanded)**: cover and controls left, Queue / Lyrics tabs
  in a pane on the right, always visible; replaces the queue bottom sheet
  that covers the controls. Boards: Now playing, Now playing · lyrics.
- **[done]** **Queue pane**: the player bar's queue button opens the queue as a side pane
  next to any screen, on its own rounded, darker surface so it reads as separate
  from the content; the content and the player bar shrink to make room.
  Board: Queue pane.
- **(not done)** **Search results (expanded)**: same stacked sections as the
  phone (already the case); song rows would gain album and duration columns, the other sections are carousels. (A
  side-by-side layout was rejected.) Board: Search.
- **[done]** **Options menu**: on expanded widths, long-press opens an M3 menu
  anchored to the item instead of the options bottom sheet. It reuses the sheet's
  content, so the header and rating row are still in it. A mouse right click
  opens it too (and the options sheet on phones). Other sheets stay bottom sheets, capped at 640 dp.
  Board: Options menu.
- **[done]** Desktop/web extras: a volume slider in the player bar where the
  app owns the volume (web only; devices with volume keys use those), space
  plays/pauses. Hover states come with the Material components already.
- **[done]** The player bar has no lyrics button: lyrics are in the now playing
  side pane.
- **[done]** The side pane's tabs use the short rounded indicator, no divider.
- Not tested on iOS or in a desktop/web window; the web target compiles but
  has no runnable entry point yet. Right click is untested (adb can't send one).

Current app on large screens (tested 2026-09-29, Pixel Tablet):
- The nav bar switches to the short style but still floats at the bottom,
  with the mini player centered over the grid; the last rows sit under both.
- Opening an album puts the whole tab (nav bar and mini player included) into
  a 384 dp list pane on the left.
- Play buttons stretch to the full width (≈ 700 dp on albums, ≈ 1180 dp on
  artists); the Library quick-access tiles stretch to ≈ 600 dp each.
- Queue opens as a bottom sheet over the player controls; lyrics are a narrow
  column in an otherwise empty screen.
- Search results are a single full-width column.

# Implementation notes

- **Loading**: screens show the cache immediately; when online, album,
  playlist, playlist-list, artist-list, genre and radio screens re-fetch from
  the server in the background and update without a spinner (a failure keeps
  the cache). Albums and Songs come from the whole-library sync and reload when
  it finishes, since re-syncing the library on every visit is too heavy.
- **Artists index**: a letter bubble shows while the index is held, letters
  shrink to fit, and the index stops above the bottom bar.

- Snackbars sit above the mini player and nav bar; inside the queue sheet they
  show under the queue header, since the sheet's bottom edge is off-screen while
  it's half open.
- Deferred server changes (delete playlist, remove from playlist) are sent when
  the Undo snackbar goes away (about 4 s, longer with accessibility timeouts).
  If the app is killed in that window, the change is never sent.
- "Change" on the quick-add snackbar takes the songs back out of the playlist
  they were saved to, then opens the sheet.
- The genre grid is always 2 columns (it ignores the grid-size setting).
- Not compiled or tested on iOS.

# To consider later

- **Rating stars in option sheets**: they sit right where a stray tap rates the
  item. Keeping them as they are for now.
- **Nav bar legibility**: the nav bar is transparent and content shows through
  behind the labels; consider a solid surface or a stronger fade.
- **Artist page**: bio overlaid on the photo, before the artist's name, with
  weak contrast. Idea: name first, short expandable bio below.
- **Recently added**: opens a screen titled "Albums". It shouldn't show that;
  decide what it should show instead.
- **Mini player when nothing plays**: "Not playing" takes a lot of space with
  disabled buttons. Other ideas pending.

# Desktop port (JVM / Compose Desktop)

The phone/tablet UI already adapts via window size classes (see "Tablet &
desktop"), so the desktop app is a thin Compose Desktop shell around the
shared `:loak` module plus a JVM playback backend.

Current state:

- New `jvm("desktop")` target on `:loak` with platform actuals: JVM-sound
  player (`DesktopMediaPlayerViewModel`), real file storage + clipboard/share,
  `Desktop.browse` links, SQLite-bundled Room databases under
  `~/.local/share/Loak` / `~/Library/Application Support/Loak` /
  `%LOCALAPPDATA%\Loak`, and no-op managers where desktop lacks the subsystem
  (app icon, audio gain, permissions, animated icons).
- New `:loakDesktop` app module packages native installers with the
  **Nucleus Gradle plugin** (jlink + jpackage app image, then electron-builder,
  so packaging needs Node.js): DMG on macOS, MSI/EXE on Windows, DEB/RPM/AppImage
  on Linux. On macOS it also compiles the Icon Composer icon into `Assets.car`.
  Portable archives: `.tar.gz` for Linux (preserves POSIX file permissions
  and executable bits), `.zip` for Windows, and `.zip` for macOS (contains
  the raw `Loak.app` bundle, the standard payload format for auto-updaters
  such as Sparkle; DMG remains the user-facing installer).
  The jlink runtime is kept lean with an explicit module list (~84 MB on Linux
  instead of ~166 MB with `includeAllModules`).
- **Window responsiveness**: Desktop uses `calculateWindowSizeClass()` to
  dynamically track the actual window dimensions, adapting between Compact
  (< 600 dp; phone layout with bottom bar & mini-player), Medium (600–839 dp;
  navigation rail), and Expanded (≥ 840 dp; floating player bar & queue pane).
- CI (`build.yml` → `build-desktop`) builds the three platform installers on
  each OS runner and attaches them to the `nightly` and `v*` releases.
- Playback: MP3 (via mp3spi/JLayer) and WAV/AIFF through Java Sound. Progress,
  pause/resume and volume are wired; shuffle, repeat, next/previous and queue
  state follow the iOS semantics. Scrobbling reuses the shared `ScrobbleManager`.

Deferred / gaps (tracked here, not implemented):

- **Auto-update** for desktop (Tauri-style or a small updater checking
  `api.github.com/repos/depau/loak/releases/latest` like the in-app one).
  The app has no self-update path yet.
- **GraalVM Native Image (AOT)** to cut the jlink runtime to a single ~50 MB
  binary with faster startup and lower memory. Caveats: reflection config per
  dependency (Room, Coil, Ktor, mp3spi can trip), slower CI builds, and
  re-breakage risk on every dependency bump — do after the baseline is stable.
- **FLAC/opus streaming**: Java Sound needs codecs (e.g. jflac / javv).
  Transcoding in the Subsonic server settings is the current workaround.
- **Seek precision**: Java Sound seeks restart from a byte estimate, not a
  sample-exact frame; gapless playback is not implemented.
- **App icon variants** (`AppIconManager`) and **AudioGain** (ReplayGain,
  equaliser) are no-ops on desktop.
- **Real network reachability** (`ConnectivityManager.isOnline` is always true
  on desktop); cellular is always false.
- **[done]** **Device-native media keys / Now Playing integration** (MPRIS on Linux,
  SMTC on Windows, Now Playing on macOS via Nucleus `MediaControlService`, plus web
  `MediaSession` action handlers and in-app Compose media/space shortcuts).

## Instant mix

- **[done]** **Instant mix**: an "Instant mix" row in Song options (all
  variants), Album options and Artist options, plus a Mix button on the Artist page.
  It replaces the queue with up to 50 similar songs (undo snackbar) via
  `getSimilarSongs(id)` for songs/albums, `getSimilarSongsID3(artistId)` for artists;
  both already exist in subsonic-client. The app uses `getSimilarSongs` for all three
  (OpenSubsonic says it takes artist ids too). The queue shows "Instant mix · <seed>" on
  the line under the count, where "From Desktop" goes, while the queue holds only mix songs.
  Empty result → "No similar songs found" snackbar, queue untouched.
  Boards: Song options, Album options, Artist options, Song options (player), Artist,
  Queue (instant mix), note on the Player page.
- **[done]** **Auto-fill queue uses similar songs**: adds up to 10; same call with the current song,
  falls back to a random song. Board: Playback.
- **[done]** **Mix to here** (servers with the OpenSubsonic `sonicSimilarity` extension,
  e.g. Navidrome + AudioMuse-AI): a Song options row that replaces the rest of the queue
  with a path from the current song into the chosen one (`findSonicPath`, called by hand
  because subsonic-client sends `stopSongId`), with undo. Board: Song options.
- **[done]** **Tablet & desktop**: the options menu gets Instant mix (same composable as
  the sheets); the queue pane shows the same label. Board: Options menu, note on the
  Tablet & desktop page.
- Not planned: similarity scores (`getSonicSimilarTracks`); nothing in the UI shows them.

## Home (proposal)

- **[done]** A **Home** tab replaces Library for now (nav: Home, Albums, Playlists,
  Artists), modelled on YT Music Home / Home — Relax and limited to what OpenSubsonic
  (Navidrome + AudioMuse-AI) can back. Boards: Home, Home — Electronic chip / Genre page
  (Library page); the note beside them gives the data source for each shelf.
- **Speed dial** (first, plain title): 3 pages of 3×3; page 1 ends with a dice tile (random song →
  its radio). Playlists, albums and songs; lists show a caret and open, songs play their
  radio. Pinned first, then most played playlists (local play log: the API has no playlist
  play counts), frequent albums, top songs. Pins are kept on the device. A "Pin to speed
  dial" row was added to Song, Album and Playlist options.
- **Quick picks**: a 20-song snapshot (5 pages of 4); Play all plays exactly what's
  shown, tapping a song plays its radio. Rebuilt after 30 min (never while on screen) or on
  pull to refresh. About 10 recent listens + 6 sure likes + 4 similar-song discoveries. Skips
  songs played more than 20 times in the last 5 days (local play log), and songs rated
  1–2 stars.
- **Genre chips**: top 10 by listening (frequent albums' play counts per genre), then an
  "All genres" chip → Genres. A chip filters Home in place; a genre opened from Genres
  pushes the same feed with its album grid at the end (one composable).
- Other shelves: Mixed for you, Made for you (AudioMuse `_automatic` playlists), Sonic
  journey (findSonicPath, `sonicSimilarity` only), Similar to <artist>, Recently added,
  Forgotten favourites, Playing on your server (only while another user plays), Your library.
- **Tablet & desktop**: same shelves. Expanded puts each Speed dial page in one row of 9
  and shows two Quick picks columns side by side; Medium uses a 3×3 page with the next one
  peeking in. Boards: Home, Home · medium (Tablet & desktop page).
- Deferred: a YT Music-style **Library** tab returns later.
- Implementation notes: the play log (song plays for 5 days, playlist/album starts) and
  Speed dial pins are JSON in the settings, per device. Quick picks' "recent listening"
  uses albums' last played date plus the play log, since songs carry no last played date
  in subsonic-client. Playing on your server is a raw getNowPlaying call (subsonic-client
  drops the songs).
- **[done]** Mouse: a vertical wheel over Home's horizontal lists scrolls the page; the
  lists show side arrows while hovered. The selected genre chip shows an X.
- Not built yet: the full Speed dial page behind the chevron (reorder/unpin; the chevron
  isn't shown), and on expanded windows Playing on your server / Your library stack
  instead of sitting side by side.
- Not feasible: podcasts/shows, videos, charts, trending, community, comments, recaps,
  Long listens, AudioMuse text search and sonic fingerprint (AudioMuse REST, not Subsonic).
- **[done]** **Instant mix buttons**: a small outlined Mix button (the instant-mix waves icon)
  between Shuffle and Play on album and playlist pages, and next to Play all on Quick picks.
  Small rather than large, since Play stays the main action and the artist page's large Mix
  only exists because the artist page has no Shuffle. Albums seed by album id; playlists and
  Quick picks mix the similar songs of 3 random songs (getSimilarSongs takes one id, never a
  playlist). Shown only when the server finds similar songs: no API flag exists, so the app
  probes a few random songs once per run. Boards: Album, Playlist, Smart playlist,
  Already in playlist, LgAlbum, Home, HomeGenre, Home · tablet boards.
