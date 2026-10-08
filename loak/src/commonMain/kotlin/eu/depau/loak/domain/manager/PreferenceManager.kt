package eu.depau.loak.domain.manager

import com.materialkolor.PaletteStyle
import eu.depau.loak.domain.manager.base.BasePreferenceManager
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.CoverArtQuality
import eu.depau.loak.domain.models.settings.CoverArtTapAction
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.domain.models.settings.NowPlayingBackgroundStyle
import eu.depau.loak.domain.models.settings.NowPlayingSliderStyle
import eu.depau.loak.domain.models.settings.OfflineMode
import eu.depau.loak.domain.models.settings.QueueInfoType
import eu.depau.loak.domain.models.settings.ReplayGainMode
import eu.depau.loak.domain.models.settings.StartupQueue
import eu.depau.loak.domain.models.settings.StreamingQuality
import eu.depau.loak.domain.models.settings.Theme
import eu.depau.loak.domain.models.settings.ThemeMode
import com.russhwolf.settings.Settings as KmpSettings

class PreferenceManager(
	settings: KmpSettings
) : BasePreferenceManager(settings) {
    var queueInfoType by (preference(QueueInfoType.Full))
	var nowPlayingBackgroundStyle by preference(NowPlayingBackgroundStyle.Dynamic)
	var swipeToSkip by preference(true)
	/** The queue side pane on wide windows, kept across launches. */
	var queuePaneOpen by preference(false)
	var coverArtQuality by preference(CoverArtQuality.High)
	var enableRatings by preference(true)
	var lyricsAutoscroll by preference(true)
	var lyricsBeatByBeat by preference(true)
	var lyricsKeepAlive by preference(true)
	var lyricsBlur by preference(false)
	var lyricsBrightInactive by preference(false)
	var enableScrobbling by preference(true)
	var scrobblePercentage by preference(.5f)
	var replayGainMode by preference(ReplayGainMode.Off)
	var rgAmpGain by preference(0f)
	var ampGain by preference(0f)
	var gaplessPlayback by preference(true)
	var audioOffload by preference(false)

	// TODO: better names and strings for these transcoding settings
	var streamingQualityWifi by preference(StreamingQuality.Lossless)
	// lossless over mobile data is ~5-10x the bytes; opt-in only, like every major service
	var streamingQualityCellular by preference(StreamingQuality.High)
	var isAdvancedTranscodingActive by preference(false)
	var customMaxBitrateWifi by preference(0)
	var customMaxBitrateCellular by preference(0)
	var customFormatWifi by preference("")
	var customFormatCellular by preference("")

	// keys kept from when downloads had a Wi-Fi and a mobile data quality
	var downloadQuality by preference("downloadQualityWifi", StreamingQuality.Lossless)
	var isAdvancedDownloadTranscodingActive by preference(false)
	var customDownloadMaxBitrate by preference("customDownloadMaxBitrateWifi", 0)
	var customDownloadFormat by preference("customDownloadFormatWifi", "")
	/** Off: downloads wait for Wi-Fi (an unmetered network). */
	var downloadOverCellular by preference(false)

	/**
	 * Besides [downloadOverCellular]: let downloads also use a roaming connection. Gated behind
	 * [android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING]/Network.framework's
	 * [nw_path_is_expensive]-adjacent flag; no extra permission is needed on either platform.
	 */
	var downloadOverRoaming by preference(false)

	/**
	 * Only download while the device is charging (a "don't wear the battery" guard). Off by
	 * default: downloads run whenever the other conditions allow it. On Android this also adds
	 * [androidx.work.Constraints.Builder.setRequiresCharging] to the download worker's wake-ups.
	 */
	var downloadOnlyWhileCharging by preference(false)

	var nowPlayingSongInfo by preference(true)
	var nowPlayingSliderStyle by preference(NowPlayingSliderStyle.Yoyo)
	var nowPlayingCoverArtAction by preference(CoverArtTapAction.TogglePlayback)
	var customHeaders by preference("")
	/** Shown on other devices as where a synced queue came from; blank = the system name. */
	var deviceName by preference("")
	/** Send crash reports to Sentry; default on (opt-out). Read at boot by SentrySetup. */
	var crashReportingEnabled by preference(true)
	var checkForUpdates by preference(true)
	var explicitContentPlayback by preference(ExplicitContentPlayback.Allowed)
	var autoFillQueue by preference(true)
	/** Save the queue to the server as playback goes, for the user's other devices. */
	var queueSyncEnabled by preference(true)
	var startupQueue by preference(StartupQueue.Server)
	var startupPlaylistId by preference("")
	/** Id of the playlist songs were last saved to; "Add to playlist" goes straight there. */
	var lastPlaylistId by preference("")

	/** Tidy names, badges and warnings for AudioMuse-AI's playlists. */
	var audioMuseIntegration by preference(true)

	/** AudioMuse-AI's shelves on Home: Made for you, radios, moods, Make something new. */
	var audioMuseHome by preference(true)

	/** Describe a mix and search by sound. */
	var audioMuseDescribe by preference(true)

	/** Ask AI for a playlist (when AudioMuse-AI has an AI service). */
	var audioMuseAskAi by preference(true)

	/** Smart playlist tools (create, edit, rules) on servers that support them. */
	var smartPlaylistsEnabled by preference(true)

	/** The integrations list the user last saw on the setup wizard's last page. */
	var integrationsSeen by preference(0)

	// AudioMuse-AI's own API; an empty address means not connected
	var audioMuseUrl by preference("")
	var audioMuseUsername by preference("")
	var audioMusePassword by preference("")
	var audioMuseToken by preference("")
	/** Extra HTTP headers for AudioMuse-AI calls, one "Key:Value" per line. */
	var audioMuseCustomHeaders by preference("")
	/** Also send the server's customHeaders with AudioMuse-AI calls, on top of audioMuseCustomHeaders. */
	var audioMuseInheritServerHeaders by preference(false)
	/** In-app volume, where the platform has no hardware volume keys (web). */
	var playerVolume by preference(1f)

	// navigation bar settings
	var bottomBarCollapseMode by preference(BottomBarCollapseMode.Never)
	var miniPlayerStyle by preference(MiniPlayerStyle.Detached)

	// theme related settings
	var theme by preference(Theme.Dynamic)
	var themeMode by preference(ThemeMode.System)
	var dynamicTheming by preference(true)
	var paletteStyle by preference(PaletteStyle.TonalSpot)
	var paletteAccentH by preference(0f)

	// sync related settings
	var lastFullSyncTime by preference(0L)
	// getScanStatus count at the last successful full pull, -1 = unknown
	var lastScanCount by preference(-1)

	// sorting/view mode preferences
	var albumListViewMode by preference(ListViewMode.Grid)
	var playlistListViewMode by preference(ListViewMode.List)
	var artistListViewMode by preference(ListViewMode.List)

	// the library lists' last chosen sort; album/song sorts are JSON, blank = the default
	var albumSorting by preference("")
	var albumSortReversed by preference(false)
	var songSorting by preference("")
	var songSortReversed by preference(false)
	var artistSorting by preference(DomainArtistListType.AlphabeticalByName)
	var playlistSorting by preference(DomainPlaylistListType.Name)
	var playlistSortReversed by preference(false)

	// these values are bitmasks of `DomainFilter`
	var albumFilters by preference(0)
	var songFilters by preference(0)
	var artistFilters by preference(0)
	var playlistFilters by preference(0)

	fun customHeadersMap(): Map<String, String> = customHeadersMap(customHeaders)

	/** Parses a newline-separated list of `Key:Value` lines into a headers map. */
	fun customHeadersMap(raw: String): Map<String, String> = buildMap {
		for (line in raw.lines()) {
			val parts = line.split(":", limit = 2)
			if (parts.size < 2) continue

			val rawKey = parts[0]
			val rawValue = parts[1]

			val key = rawKey.trim()
			val value = rawValue.trim()
			if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
		}
	}

	var offlineMode by preference(OfflineMode.Auto)

	// streamed songs kept on disk (AudioStore); downloads don't count towards the limit
	var audioCacheEnabled by preference(true)
	var audioCacheMaxBytes by preference(2L * 1024 * 1024 * 1024)
}
