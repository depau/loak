package eu.depau.loak.ui.components.sheets

import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.icons.outlined.Flask
import eu.depau.loak.ui.screens.alchemy.toIngredient
import eu.depau.loak.generated.resources.action_song_alchemy_with
import eu.depau.loak.generated.resources.action_similar_lyrics_sound
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.icons.outlined.Lyrics
import androidx.compose.runtime.collectAsState
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.songsEditableBy
import eu.depau.loak.domain.repositories.HomeRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import eu.depau.loak.generated.resources.info_end_of_queue
import eu.depau.loak.generated.resources.info_left
import eu.depau.loak.generated.resources.info_songs_left
import eu.depau.loak.generated.resources.title_playback
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItemColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.generated.resources.action_back
import eu.depau.loak.generated.resources.info_mix_alchemy
import eu.depau.loak.generated.resources.info_mix_from
import eu.depau.loak.generated.resources.info_mix_instant
import eu.depau.loak.generated.resources.info_mix_lyrics_sound
import eu.depau.loak.generated.resources.info_mix_to_here
import eu.depau.loak.generated.resources.title_make_a_mix
import eu.depau.loak.icons.outlined.ArrowBack
import eu.depau.loak.icons.outlined.ChevronForward
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_another_playlist
import eu.depau.loak.generated.resources.action_add_to_playlist
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_instant_mix
import eu.depau.loak.generated.resources.action_mix_to_here
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_from_playlist
import eu.depau.loak.generated.resources.action_remove_from_queue
import eu.depau.loak.generated.resources.action_remove_star
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.action_sleep_timer
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.action_track_info
import eu.depau.loak.generated.resources.label_speed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SleepTimerManager
import eu.depau.loak.domain.manager.SleepTimerMode
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.canUserShare
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Bedtime
import eu.depau.loak.icons.outlined.InstantMix
import eu.depau.loak.icons.outlined.SonicPath
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.Speed
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Repeat
import eu.depau.loak.icons.filled.ShuffleOn
import eu.depau.loak.icons.filled.RepeatOn
import eu.depau.loak.icons.filled.RepeatOneOn
import eu.depau.loak.generated.resources.action_shuffle
import eu.depau.loak.generated.resources.info_repeat_off
import eu.depau.loak.generated.resources.info_repeat_one
import eu.depau.loak.generated.resources.info_repeat_all
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.RatingRow
import eu.depau.loak.ui.components.common.TooltipBox
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.InlineExplicitIcon
import eu.depau.loak.ui.util.buildSongInfoString
import eu.depau.loak.ui.util.label
import eu.depau.loak.ui.util.rememberColorSchemeFromCoverArt
import eu.depau.loak.ui.theme.SmallCoverArtShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSheet(
	onDismissRequest: () -> Unit,
	song: DomainSong,
	collection: DomainSongCollection? = null,
	starred: Boolean? = null,
	onSetStarred: ((Boolean) -> Unit)? = null,
	onShare: (() -> Unit)? = null,
	onPlayNext: (() -> Unit)? = null,
	onAddToQueue: (() -> Unit)? = null,
	onTrackInfo: (() -> Unit)? = null,
	/** Makes the album in the header a link; its artists always are. */
	onViewAlbum: (() -> Unit)? = null,
	onAddToPlaylist: (() -> Unit)? = null,
	onRemoveFromPlaylist: (() -> Unit)? = null,
	onRemoveFromQueue: (() -> Unit)? = null,
	downloadStatus: DownloadStatus? = null,
	onDownload: (() -> Unit)? = null,
	onCancelDownload: (() -> Unit)? = null,
	onDeleteDownload: (() -> Unit)? = null,
	rating: Int? = null,
	onSetRating: ((Int) -> Unit)? = null,
	showSleepTimer: Boolean = false,
	onSleepTimer: (() -> Unit)? = null,
	showPlaybackSpeed: Boolean = false,
	onPlaybackSpeed: (() -> Unit)? = null,
	/** The player's transport row is too narrow for shuffle and repeat, so they show here. */
	showShuffleRepeat: Boolean = false,
	useSongTheme: Boolean = true
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val sessionManager = koinInject<SessionManager>()
	val exportManager = koinInject<ExportManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val audioMuseInfo by koinInject<AudioMuseManager>().info.collectAsState()
	val player = koinInject<MediaPlayerViewModel>()
	val audioMuseRepository = koinInject<AudioMuseRepository>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val canFindSonicPaths by produceState(false) { value = player.canFindSonicPaths() }

	val sleepTimerManager = koinInject<SleepTimerManager>()
	val sleepTimerMode by sleepTimerManager.mode.collectAsStateWithLifecycle()
	val contentPadding = PaddingValues(horizontal = 16.dp)

	val colorScheme = if (useSongTheme) rememberColorSchemeFromCoverArt(song.coverArtId) else null
	var mixPage by rememberSaveable { mutableStateOf(false) }

	val backStack = LocalNavStack.current
	// offline, what needs the server is greyed out; starring, rating, downloading and playlist
	// edits are queued
	val online = LocalAvailability.current.online
	val playable = LocalAvailability.current.song(song.id)

	LoakTheme(colorScheme) {
		val colors = ListItemDefaults.colors(
			containerColor = Color.Transparent,
			trailingIconColor = MaterialTheme.colorScheme.onSurface,
			headlineColor = MaterialTheme.colorScheme.onSurface
		)
		// from the song playing now into this one
		val playingId = playerState.currentSong?.id
		val mixes = buildList {
			add(MixOption(
				stringResource(Res.string.action_instant_mix),
				stringResource(Res.string.info_mix_instant),
				Icons.Outlined.InstantMix
			) {
				player.playInstantMix(song.id, song.title, seed = song)
				onDismissRequest()
			})
			if (canFindSonicPaths && playingId != null && playingId != song.id) add(MixOption(
				stringResource(Res.string.action_mix_to_here),
				stringResource(Res.string.info_mix_to_here),
				Icons.Outlined.SonicPath
			) {
				player.playSonicPathTo(song)
				onDismissRequest()
			})
			if (audioMuseInfo != null) {
				add(MixOption(
					stringResource(Res.string.action_song_alchemy_with),
					stringResource(Res.string.info_mix_alchemy),
					Icons.Outlined.Flask
				) {
					// leaving the sheet for a screen underneath closes the player, if it's up
					backStack.remove(Screen.NowPlaying)
					backStack.add(Screen.Alchemy(song.toIngredient()))
					onDismissRequest()
				})
				if (audioMuseInfo?.lyricsSearch == true) add(MixOption(
					stringResource(Res.string.action_similar_lyrics_sound),
					stringResource(Res.string.info_mix_lyrics_sound),
					Icons.Outlined.Lyrics
				) {
					player.playMix(song.title) { audioMuseRepository.similarLyricsAndSound(song.id) }
					onDismissRequest()
				})
			}
		}
		ModalBottomSheet(
			onDismissRequest = onDismissRequest,
			menuOnWideWindows = true,
			onBack = if (mixPage) ({ mixPage = false }) else null,
			containerColor = MaterialTheme.colorScheme.surface,
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			),
			contentWindowInsets = {
				BottomSheetDefaults.modalWindowInsets.add(
					WindowInsets(
						left = 8.dp,
						right = 8.dp
					)
				)
			}
		) {
			Spacer(Modifier.height(16.dp))
			if (mixPage) {
				MixPage(song, mixes, online, colors, contentPadding, onBack = { mixPage = false })
				return@ModalBottomSheet
			}

			ListItem(
				headlineContent = {
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIcon,
					)
				},
				supportingContent = {
					MarqueeText(
						buildSongInfoString(
							song = song,
							onClickArtist = {
								onDismissRequest()
								// leaving the sheet for a screen underneath closes the player, if it's up
								backStack.remove(Screen.NowPlaying)
								backStack.add(Screen.ArtistDetail(it))
							},
							onClickAlbum = onViewAlbum?.let { view ->
								{
									onDismissRequest()
									view()
								}
							},
							linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
						)
					)
				},
				trailingContent = onTrackInfo?.let { trackInfo ->
					{
						val label = stringResource(Res.string.action_track_info)
						TooltipBox(label) {
							IconButton(onClick = {
								onDismissRequest()
								trackInfo()
							}) {
								Icon(Icons.Outlined.Info, label)
							}
						}
					}
				},
				leadingContent = {
					CoverArt(
						coverArtId = song.coverArtId,
						modifier = Modifier.size(50.dp),
						shape = SmallCoverArtShape
					)
				},
				colors = colors
			)
			if (rating != null && onSetRating != null && preferenceManager.enableRatings) {
				RatingRow(
					rating = rating,
					setRating = onSetRating
				)
				Spacer(Modifier.height(14.dp))
			}

			SheetActionBar(
				listOfNotNull(
					onPlayNext?.let { playNext ->
						SheetAction(
							stringResource(Res.string.action_play_next),
							Icons.Outlined.QueuePlayNext,
							{ playNext(); onDismissRequest() },
							enabled = playable
						)
					},
					onAddToQueue?.let { addToQueue ->
						SheetAction(
							stringResource(Res.string.action_add_to_queue),
							Icons.Outlined.Queue,
							{ addToQueue(); onDismissRequest() },
							enabled = playable
						)
					},
					if (starred != null && onSetStarred != null) SheetAction(
						stringResource(if (starred) Res.string.action_remove_star else Res.string.action_star),
						if (starred) Icons.Filled.Star else Icons.Outlined.Star,
						{ onSetStarred(!starred); onDismissRequest() },
						checked = starred
					) else null,
					downloadAction(
						downloadStatus,
						onDownload = onDownload,
						onCancel = onCancelDownload,
						onDelete = onDeleteDownload,
						onDismissRequest = onDismissRequest
					),
					if (onShare != null && sessionManager.canUserShare()) SheetAction(
						stringResource(Res.string.action_share),
						Icons.Outlined.Share,
						{ onShare(); onDismissRequest() },
						enabled = online
					) else null
				)
			)

			Column(Modifier.verticalScroll(rememberScrollState())) {
				if (onRemoveFromPlaylist != null && (collection as? DomainPlaylist)?.songsEditableBy(sessionManager.username) == true) {
					ListItem(
						content = { Text(stringResource(Res.string.action_remove_from_playlist)) },
						leadingContent = { Icon(Icons.Outlined.PlaylistRemove, null) },
						onClick = {
							onRemoveFromPlaylist()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onRemoveFromQueue != null) {
					ListItem(
						content = { Text(stringResource(Res.string.action_remove_from_queue)) },
						leadingContent = { Icon(Icons.Outlined.PlaylistRemove, null) },
						onClick = {
							onRemoveFromQueue()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onAddToPlaylist != null) {
					ListItem(
						content = {
							Text(
								stringResource(
									if (collection != null && collection !is DomainAlbum)
										Res.string.action_add_to_another_playlist
									else Res.string.action_add_to_playlist
								)
							)
						},
						leadingContent = { Icon(Icons.Outlined.PlaylistAdd, null) },
						onClick = {
							onAddToPlaylist()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				// one plain row while instant mix is the only kind, a sub-page once there are more
				if (mixes.size == 1) {
					val mix = mixes.single()
					ListItem(
						content = { Text(mix.label) },
						leadingContent = { Icon(mix.icon, null) },
						onClick = mix.onClick,
						enabled = online,
						colors = colors,
						contentPadding = contentPadding
					)
				} else {
					ListItem(
						content = { Text(stringResource(Res.string.title_make_a_mix)) },
						supportingContent = {
							Text(mixes.joinToString { it.label }, maxLines = 1, overflow = TextOverflow.Ellipsis)
						},
						leadingContent = { Icon(Icons.Outlined.InstantMix, null) },
						trailingContent = { Icon(Icons.Outlined.ChevronForward, null) },
						onClick = { mixPage = true },
						enabled = online,
						colors = colors,
						contentPadding = contentPadding
					)
				}

				SpeedDialPinItem(HomeRepository.keyOf(song), colors, contentPadding, onDismissRequest)

				if (showSleepTimer || showShuffleRepeat || showPlaybackSpeed) {
					val shuffle = playerState.isShuffleEnabled
					val repeat = playerState.repeatMode
					Text(
						stringResource(Res.string.title_playback),
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
					)
					PlaybackTiles(
						listOfNotNull(
							if (showSleepTimer) PlaybackTile(
								stringResource(Res.string.action_sleep_timer),
								when (val mode = sleepTimerMode) {
									is SleepTimerMode.Time ->
										stringResource(Res.string.info_left, sleepTimerManager.timeLeft?.label() ?: "")
									is SleepTimerMode.Songs ->
										pluralStringResource(Res.plurals.info_songs_left, mode.remaining, mode.remaining)
									is SleepTimerMode.EndOfQueue -> stringResource(Res.string.info_end_of_queue)
									else -> null
								},
								Icons.Outlined.Bedtime,
								checked = sleepTimerMode !is SleepTimerMode.Disabled,
								onClick = { onSleepTimer?.invoke() }
							) else null,
							if (showPlaybackSpeed) PlaybackTile(
								stringResource(Res.string.label_speed),
								"${playerState.playbackSpeed}x",
								Icons.Outlined.Speed,
								checked = playerState.playbackSpeed != 1f,
								onClick = dropUnlessResumed { onPlaybackSpeed?.invoke() }
							) else null,
							if (showShuffleRepeat) PlaybackTile(
								stringResource(Res.string.action_shuffle),
								null,
								if (shuffle) Icons.Filled.ShuffleOn else Icons.Outlined.Shuffle,
								checked = shuffle,
								onClick = { player.toggleShuffle() }
							) else null,
							if (showShuffleRepeat) PlaybackTile(
								stringResource(
									when (repeat) {
										1 -> Res.string.info_repeat_one
										2 -> Res.string.info_repeat_all
										else -> Res.string.info_repeat_off
									}
								),
								null,
								when (repeat) {
									1 -> Icons.Filled.RepeatOneOn
									2 -> Icons.Filled.RepeatOn
									else -> Icons.Outlined.Repeat
								},
								checked = repeat != 0,
								onClick = { player.toggleRepeat() }
							) else null
						)
					)
				}
			}
		}
	}
}

private class MixOption(
	val label: String,
	val description: String,
	val icon: ImageVector,
	val onClick: () -> Unit
)

/** The kinds of mix a song can start, with a way back to the rest of its options. */
@Composable
private fun MixPage(
	song: DomainSong,
	mixes: List<MixOption>,
	enabled: Boolean,
	colors: ListItemColors,
	contentPadding: PaddingValues,
	onBack: () -> Unit
) {
	ListItem(
		headlineContent = { Text(stringResource(Res.string.title_make_a_mix)) },
		supportingContent = {
			Text(
				stringResource(Res.string.info_mix_from, song.title),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		},
		leadingContent = {
			val label = stringResource(Res.string.action_back)
			TooltipBox(label) {
				IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, label) }
			}
		},
		colors = colors
	)
	Column(Modifier.verticalScroll(rememberScrollState())) {
		mixes.forEach { mix ->
			ListItem(
				content = { Text(mix.label) },
				supportingContent = { Text(mix.description) },
				leadingContent = { Icon(mix.icon, null) },
				onClick = mix.onClick,
				enabled = enabled,
				colors = colors,
				contentPadding = contentPadding
			)
		}
	}
}

private class PlaybackTile(
	val name: String,
	val value: String?,
	val icon: ImageVector,
	val checked: Boolean,
	val onClick: () -> Unit
)

/** The player's settings as toggle tiles, two a row, each showing its current value. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaybackTiles(tiles: List<PlaybackTile>) {
	val gap = ButtonGroupDefaults.ConnectedSpaceBetween
	Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
		tiles.chunked(2).forEach { row ->
			Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
				row.forEachIndexed { index, tile ->
					ToggleButton(
						checked = tile.checked,
						onCheckedChange = { tile.onClick() },
						shapes = when {
							row.size == 1 -> ToggleButtonDefaults.shapes()
							index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
							else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
						},
						contentPadding = PaddingValues(horizontal = 16.dp),
						modifier = Modifier.weight(1f).height(64.dp)
					) {
						Icon(tile.icon, null)
						Spacer(Modifier.width(12.dp))
						Column(Modifier.weight(1f)) {
							Text(
								tile.name,
								style = MaterialTheme.typography.labelLarge,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
							tile.value?.let {
								Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
							}
						}
					}
				}
			}
		}
	}
}
