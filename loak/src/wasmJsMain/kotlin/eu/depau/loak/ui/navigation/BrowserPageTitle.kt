package eu.depau.loak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.generated.resources.*
import eu.depau.loak.ui.components.common.playlistName
import eu.depau.loak.ui.screens.discover.discoverListTitle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Page names are localized like the app; identifiers are resolved locally, never shown. */
@Composable
internal fun browserPageTitle(screen: Screen?, onTitle: (String) -> Unit) {
	val name = when (screen) {
		is Screen.CollectionDetail -> {
			val albums = koinInject<AlbumDao>()
			val playlists = koinInject<PlaylistDao>()
			val sync = koinInject<SyncManager>()
			val id = screen.collectionId
			val album by remember(id) { albums.getAlbumEntityById(id) }.collectAsState(null)
			val playlist by remember(id) { playlists.getPlaylistEntityById(sync.playlistId(id)) }
				.collectAsState(null)
			playlist?.let { playlistName(it.name, it.validUntil).display }
				?: album?.name ?: stringResource(Res.string.title_library)
		}
		is Screen.ArtistDetail -> {
			val dao = koinInject<ArtistDao>()
			val name = key(screen) { produceState<String?>(null) { value = dao.getArtistById(screen.artist)?.name } }
			name.value ?: stringResource(Res.string.title_artists)
		}
		is Screen.SongDetailScreen -> {
			val dao = koinInject<SongDao>()
			val name = key(screen) { produceState<String?>(null) { value = dao.getSongById(screen.songId)?.title } }
			name.value ?: stringResource(Res.string.title_track_info)
		}
		is Screen.Home -> stringResource(Res.string.title_home)
		is Screen.Starred -> stringResource(Res.string.title_starred)
		is Screen.PlaylistList -> stringResource(Res.string.title_playlists)
		is Screen.ArtistList -> stringResource(Res.string.title_artists)
		is Screen.AlbumList -> stringResource(Res.string.title_albums)
		is Screen.GenreList -> stringResource(Res.string.title_genres)
		is Screen.GenreDetail -> screen.genreName
		is Screen.SongList -> stringResource(Res.string.title_songs)
		Screen.Library -> stringResource(Res.string.title_library)
		is Screen.Alchemy -> stringResource(Res.string.title_song_alchemy)
		is Screen.AskAI -> stringResource(Res.string.action_ask_ai_playlist)
		is Screen.DescribeMix -> stringResource(Res.string.title_describe_mix)
		Screen.SongPath -> stringResource(Res.string.title_song_path)
		Screen.Explore -> stringResource(Res.string.title_explore)
		Screen.Discover -> stringResource(Res.string.title_discover)
		is Screen.DiscoverSearch -> stringResource(Res.string.title_search)
		is Screen.DiscoverAlbum -> screen.title ?: stringResource(Res.string.title_albums)
		is Screen.DiscoverArtist -> screen.name ?: stringResource(Res.string.title_artists)
		is Screen.DiscoverList -> discoverListTitle(screen.type)
		Screen.LidarrQueue -> stringResource(Res.string.option_download_queue)
		is Screen.Mood -> screen.mood.label
		Screen.SoundMap -> stringResource(Res.string.title_sound_map)
		is Screen.Downloads -> stringResource(Res.string.title_downloads)
		is Screen.RadioList -> stringResource(Res.string.title_radios)
		Screen.Login -> stringResource(Res.string.title_connect_server)
		Screen.SetupFeatures -> stringResource(Res.string.title_make_loak_yours)
		is Screen.WhatsNew -> stringResource(Res.string.title_new_in_loak)
		Screen.Recap -> stringResource(Res.string.title_recap)
		is Screen.Search -> stringResource(Res.string.title_search)
		Screen.ShareList -> stringResource(Res.string.title_shares)
		Screen.Settings.Root -> stringResource(Res.string.title_settings)
		Screen.Settings.Appearance -> stringResource(Res.string.title_appearance)
		Screen.Settings.Playback -> stringResource(Res.string.title_playback)
		Screen.Settings.Server -> stringResource(Res.string.title_server_account)
		Screen.Settings.Advanced -> stringResource(Res.string.title_advanced)
		Screen.Settings.Tabs -> stringResource(Res.string.title_tabs)
		Screen.Settings.Player -> stringResource(Res.string.title_player)
		Screen.Settings.About -> stringResource(Res.string.title_about_loak)
		Screen.Settings.DataStorage -> stringResource(Res.string.title_downloads_storage)
		Screen.Settings.NetworkStats -> stringResource(Res.string.title_network_stats)
		Screen.Settings.Sound -> stringResource(Res.string.title_sound_quality)
		Screen.Settings.Transcoding -> stringResource(Res.string.option_custom_transcoding)
		Screen.Settings.CustomHeaders -> stringResource(Res.string.option_custom_headers)
		Screen.Settings.Logs -> stringResource(Res.string.title_logs)
		Screen.Settings.Equaliser -> stringResource(Res.string.option_equaliser)
		Screen.Settings.AudioMuse -> stringResource(Res.string.title_audiomuse)
		Screen.Settings.AudioMuseConnect -> stringResource(Res.string.title_connect_audiomuse)
		Screen.Settings.AudioMuseCustomHeaders -> stringResource(Res.string.option_audiomuse_headers)
		Screen.Settings.Lidarr -> stringResource(Res.string.title_discover_settings)
		Screen.Settings.LidarrConnect -> stringResource(Res.string.title_lidarr)
		Screen.Settings.LidarrCustomHeaders -> stringResource(Res.string.option_lidarr_headers)
		Screen.Settings.ListenBrainz -> stringResource(Res.string.title_listenbrainz)
		Screen.Settings.ListeningHistory -> stringResource(Res.string.title_listening_history)
		is Screen.Settings.HistorySourceConnect -> stringResource(when (screen.source) {
			"koito" -> Res.string.title_connect_koito
			"lastfm" -> Res.string.title_connect_lastfm
			else -> Res.string.title_connect_listenbrainz
		})
		Screen.Settings.DiscoverSearch -> stringResource(Res.string.title_discover_sources)
		Screen.Settings.Integrations -> stringResource(Res.string.title_integrations)
		Screen.Settings.ChangeServer -> stringResource(Res.string.action_change_server)
		// Transient overlays do not become history entries.
		is Screen.ImageView, Screen.NowPlaying, Screen.PlaybackSpeed, is Screen.SongDetailSheet, null -> ""
	}
	val title = if (name.isBlank()) "Lo'ak" else "$name · Lo'ak"
	SideEffect { onTitle(title) }
}
