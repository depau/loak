package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.discover.MusicBrainzReleaseGroup
import eu.depau.loak.domain.discover.SongReleases
import eu.depau.loak.domain.discover.isSecondary
import eu.depau.loak.domain.discover.selectDefaultReleaseGroup
import eu.depau.loak.domain.manager.DiscoverBadgeState
import eu.depau.loak.domain.manager.LidarrException
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.LidarrQualityProfile
import eu.depau.loak.domain.manager.LidarrRootFolder
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.snackbars.PlayerEvent
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_and_search
import eu.depau.loak.generated.resources.action_add_and_search_count
import eu.depau.loak.generated.resources.action_add_to_lidarr
import eu.depau.loak.generated.resources.action_back
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_done
import eu.depau.loak.generated.resources.action_more_releases
import eu.depau.loak.generated.resources.action_ok
import eu.depau.loak.generated.resources.action_open_musicbrainz
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.generated.resources.badge_recommended
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_add_song_to_lidarr
import eu.depau.loak.generated.resources.info_artist_in_lidarr
import eu.depau.loak.generated.resources.info_artist_new_in_lidarr
import eu.depau.loak.generated.resources.info_download_options
import eu.depau.loak.generated.resources.info_metadata_server_down
import eu.depau.loak.generated.resources.info_not_in_lidarr
import eu.depau.loak.generated.resources.info_not_in_lidarr_try_later
import eu.depau.loak.generated.resources.label_default_for
import eu.depau.loak.generated.resources.label_free_space
import eu.depau.loak.generated.resources.notice_lidarr_add_failed
import eu.depau.loak.generated.resources.notice_lidarr_artist_added
import eu.depau.loak.generated.resources.notice_lidarr_downloading
import eu.depau.loak.generated.resources.notice_lidarr_searching
import eu.depau.loak.generated.resources.option_future_releases
import eu.depau.loak.generated.resources.subtitle_future_releases
import eu.depau.loak.generated.resources.title_add_song_to_lidarr
import eu.depau.loak.generated.resources.title_download_options
import eu.depau.loak.generated.resources.title_metadata_server_down
import eu.depau.loak.generated.resources.title_not_in_lidarr
import eu.depau.loak.generated.resources.title_quality
import eu.depau.loak.generated.resources.title_root_folder
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.ArrowBack
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.Schedule
import eu.depau.loak.icons.outlined.Settings
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.dialogs.FormDialog
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.util.Logger
import eu.depau.loak.util.toFileSize
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

private const val TAG = "LidarrAddFlow"

/** One album an add sheet adds: a MusicBrainz release group, and how the sheet shows it. */
data class AlbumPick(val releaseGroupId: String, val title: String, val subtitle: String, val coverUrl: String?)

/** Which sheet or dialog of the add flow is up. */
internal sealed interface AddStep {
	data class ChooseRelease(val song: DiscoverSong) : AddStep

	/** The add-album sheet; a Discover [album] gets identified first, while [picks] is null. */
	data class Albums(
		val artistName: String,
		val picks: List<AlbumPick>? = null,
		val existingArtist: Boolean = false,
		val album: DiscoverAlbum? = null
	) : AddStep

	data class Artist(val artist: DiscoverArtist) : AddStep
	data class NotInLidarr(val musicBrainzUrl: String) : AddStep
	data object MetadataDown : AddStep
}

/** Something Lidarr's metadata server (or MusicBrainz) doesn't know: "Not in Lidarr yet". */
internal class NotInLidarrException(val musicBrainzUrl: String) : Exception()

internal fun coverArtUrl(releaseGroupId: String) =
	"https://coverartarchive.org/release-group/$releaseGroupId/front-250"

internal fun musicBrainzSearchUrl(type: String, query: String) =
	"https://musicbrainz.org/search?type=$type&query=${query.encodeURLParameter()}"

/**
 * Adds Discover songs, albums and artists to Lidarr through the sheets of [rememberLidarrAddFlow].
 * Adds run app-wide (they wait on Lidarr for up to minutes) and report through the snackbar.
 */
@Stable
class LidarrAddFlow internal constructor(
	private val lidarr: LidarrManager,
	private val snackBarManager: SnackBarManager,
	private val backStack: NavBackStack<NavKey>
) {
	internal var step by mutableStateOf<AddStep?>(null)

	/** Picks the releases containing [song], then adds them. */
	fun addSong(song: DiscoverSong) {
		step = AddStep.ChooseRelease(song)
	}

	fun addAlbum(album: DiscoverAlbum) {
		step = AddStep.Albums(album.artist, album = album)
	}

	fun addArtist(artist: DiscoverArtist) {
		step = AddStep.Artist(artist)
	}

	/** Runs [block]; a Lidarr miss or metadata outage becomes its dialog, anything else a snackbar. */
	internal suspend fun guard(block: suspend () -> Unit) {
		try {
			block()
		} catch (e: CancellationException) {
			throw e
		} catch (e: NotInLidarrException) {
			step = AddStep.NotInLidarr(e.musicBrainzUrl)
		} catch (e: Exception) {
			if (e is LidarrException && (e.status?.value ?: 0) >= 500) {
				step = AddStep.MetadataDown
				return
			}
			Logger.e(TAG, "Lidarr add flow failed", e)
			step = null
			snackBarManager.notify(Res.string.notice_lidarr_add_failed, e.message.orEmpty())
		}
	}

	/**
	 * Whether the release groups' artist is already in Lidarr, by the album lookup Lidarr adds
	 * with; throws [NotInLidarrException] for a group Lidarr's metadata server doesn't know.
	 */
	internal suspend fun artistExistsFor(releaseGroupIds: List<String>): Boolean {
		var existing = false
		for (id in releaseGroupIds) {
			val album = lidarr.lookupAlbumByMbId(id)
				?: throw NotInLidarrException("https://musicbrainz.org/release-group/$id")
			val artistId = album["artist"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: 0L
			if (artistId > 0) existing = true
		}
		return existing
	}

	/**
	 * Adds [picks] in order (the first one creates a new artist, the others find it), then says
	 * whether Lidarr's search had something downloading within ~30 s. [rootFolder] is null for an
	 * existing artist, whose own settings apply.
	 */
	internal fun addAlbums(
		picks: List<AlbumPick>,
		future: Boolean,
		rootFolder: LidarrRootFolder?,
		qualityProfileId: Long
	) {
		step = null
		snackBarManager.launch {
			try {
				val metadataProfileId = when {
					rootFolder == null -> 0L
					future -> rootFolder.defaultMetadataProfileId
					else -> lidarr.findOrCreateNoneProfile().id
				}
				var artistId = 0L
				for (pick in picks) {
					artistId = lidarr.addAlbum(
						pick.releaseGroupId, pick.title, future,
						rootFolder?.path.orEmpty(), qualityProfileId, metadataProfileId
					)
				}
				val downloading = withTimeoutOrNull(30.seconds) {
					while (lidarr.catalog.value.queue.none { it.artistId == artistId }) {
						delay(5.seconds)
						lidarr.refreshQueue()
					}
				} != null
				snackBarManager.notify(
					PlayerEvent(
						if (downloading) Res.string.notice_lidarr_downloading else Res.string.notice_lidarr_searching,
						action = Res.string.action_queue,
						onAction = { backStack.add(Screen.LidarrQueue) }
					)
				)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e(TAG, "adding albums to Lidarr failed", e)
				snackBarManager.notify(Res.string.notice_lidarr_add_failed, e.message.orEmpty())
			}
		}
	}

	/** Adds an artist in the background; see [LidarrManager.addArtist] for the arguments. */
	internal fun startArtistAdd(
		artistMbid: String,
		artistName: String,
		monitor: String,
		searchNow: Boolean,
		rootFolderPath: String,
		qualityProfileId: Long,
		metadataProfileId: Long,
		monitorNewItems: String
	) {
		step = null
		snackBarManager.launch {
			try {
				lidarr.addArtist(
					artistMbid, artistName, monitor, searchNow, rootFolderPath,
					qualityProfileId, metadataProfileId, monitorNewItems
				)
				snackBarManager.notify(Res.string.notice_lidarr_artist_added, artistName)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e(TAG, "adding an artist to Lidarr failed", e)
				snackBarManager.notify(Res.string.notice_lidarr_add_failed, e.message.orEmpty())
			}
		}
	}
}

/** An add flow for this screen, with its sheets and dialogs shown while one runs. */
@Composable
fun rememberLidarrAddFlow(): LidarrAddFlow {
	val lidarr = koinInject<LidarrManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val backStack = LocalNavStack.current
	val flow = remember { LidarrAddFlow(lidarr, snackBarManager, backStack) }
	when (val step = flow.step) {
		is AddStep.ChooseRelease -> ChooseReleaseSheet(flow, step.song)
		is AddStep.Albums -> AddAlbumSheet(flow, step)
		is AddStep.Artist -> AddArtistSheet(flow, step.artist)
		is AddStep.NotInLidarr -> LidarrLookupDialog(
			Res.string.title_not_in_lidarr,
			listOf(Res.string.info_not_in_lidarr, Res.string.info_not_in_lidarr_try_later),
			step.musicBrainzUrl
		) { flow.step = null }
		AddStep.MetadataDown -> LidarrLookupDialog(
			Res.string.title_metadata_server_down,
			listOf(Res.string.info_metadata_server_down),
			null
		) { flow.step = null }
		null -> {}
	}
	return flow
}

@Composable
private fun ChooseReleaseSheet(flow: LidarrAddFlow, song: DiscoverSong) {
	val musicBrainz = koinInject<MusicBrainzClient>()
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	val scope = rememberCoroutineScope()
	var releases by remember { mutableStateOf<SongReleases?>(null) }
	// what Lidarr already gets: badged and not preselected, but still pickable to search again
	val inLidarr = remember(catalog) { catalog.albums.filter { it.monitored }.mapNotNullTo(HashSet()) { it.musicBrainzId } }
	var selected by remember { mutableStateOf(emptySet<String>()) }
	var showMore by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }

	LaunchedEffect(song) {
		flow.guard {
			val found = musicBrainz.songReleases(song.isrc, song.title, song.artist)
			if (found.groups.isEmpty()) throw NotInLidarrException(musicBrainzUrlOf(song))
			selected = setOfNotNull(selectDefaultReleaseGroup(found.groups.filter { it.id !in inLidarr })?.id)
			releases = found
		}
	}

	ModalBottomSheet(onDismissRequest = { flow.step = null }) {
		Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(stringResource(Res.string.title_add_song_to_lidarr, song.title), style = MaterialTheme.typography.headlineSmall)
			Text(
				stringResource(Res.string.info_add_song_to_lidarr),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		val found = releases
		if (found == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
		else {
			val artistName = found.artistName ?: song.artist
			val recommended = selectDefaultReleaseGroup(found.groups.filter { it.id !in inLidarr })?.id
			val (main, more) = found.groups.partition { !it.isSecondary() }
			Column(Modifier.weight(1f, fill = false).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
				val row = @Composable { group: MusicBrainzReleaseGroup ->
					ReleaseRow(group, group.id in selected, group.id in inLidarr, group.id == recommended) {
						selected = if (group.id in selected) selected - group.id else selected + group.id
					}
				}
				main.forEach { row(it) }
				if (showMore) more.forEach { row(it) }
				else if (more.isNotEmpty()) TextButton(
					onClick = { showMore = true },
					modifier = Modifier.padding(horizontal = 12.dp)
				) {
					Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
					Text(
						pluralStringResource(Res.plurals.action_more_releases, more.size, more.size),
						Modifier.padding(start = 8.dp)
					)
				}
				if (found.artistMbid != null && catalog.artists.any { it.musicBrainzId == found.artistMbid })
					Notice(stringResource(Res.string.info_artist_in_lidarr, artistName))
			}
			SheetButtons(
				confirm = pluralStringResource(Res.plurals.action_add_and_search_count, selected.size, selected.size),
				enabled = selected.isNotEmpty(),
				busy = busy,
				onCancel = { flow.step = null }
			) {
				busy = true
				scope.launch {
					flow.guard {
						val picked = found.groups.filter { it.id in selected }
						val picks = picked.map {
							AlbumPick(it.id, it.title, dotted(artistName, it.primaryType, it.firstReleaseDate?.take(4)), coverArtUrl(it.id))
						}
						// Lidarr's own lookup decides the recipe; it also catches groups it doesn't know
						if (flow.artistExistsFor(picked.map { it.id })) flow.addAlbums(picks, false, null, 0L)
						else flow.step = AddStep.Albums(artistName, picks)
					}
					busy = false
				}
			}
		}
	}
}

@Composable
private fun ReleaseRow(
	group: MusicBrainzReleaseGroup,
	checked: Boolean,
	inLidarr: Boolean,
	recommended: Boolean,
	onToggle: () -> Unit
) {
	val songs = group.trackCount?.let { pluralStringResource(Res.plurals.count_songs, it, it) }
	val kinds = listOf(group.primaryType) + group.secondaryTypes.orEmpty()
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
			.toggleable(checked, role = Role.Checkbox) { onToggle() }
			.padding(horizontal = 24.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Checkbox(checked = checked, onCheckedChange = null)
		DiscoverArt(coverArtUrl(group.id), 48.dp)
		Column(Modifier.weight(1f)) {
			Text(group.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				Text(
					dotted(*(kinds + group.firstReleaseDate?.take(4) + songs).toTypedArray()),
					modifier = Modifier.weight(1f, fill = false),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				if (inLidarr) DiscoverBadge(DiscoverBadgeState.InLidarr)
			}
			if (recommended) Surface(
				modifier = Modifier.padding(top = 4.dp),
				shape = RoundedCornerShape(6.dp),
				color = MaterialTheme.colorScheme.primaryContainer
			) {
				Text(
					stringResource(Res.string.badge_recommended),
					Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.SemiBold
				)
			}
		}
	}
}

/**
 * The add-album sheet: for a new artist, "Also get future releases" and the download options
 * (root folder and quality, remembered from the last add); for an artist Lidarr has, just the
 * confirmation. Identifies a Discover album first.
 */
@Composable
private fun AddAlbumSheet(flow: LidarrAddFlow, step: AddStep.Albums) {
	val lidarr = koinInject<LidarrManager>()
	val musicBrainz = koinInject<MusicBrainzClient>()
	val deezer = koinInject<DeezerClient>()
	val preferences = koinInject<PreferenceManager>()
	val options by produceState<Pair<List<LidarrRootFolder>, List<LidarrQualityProfile>>?>(null) {
		value = lidarr.getRootFolders() to lidarr.getQualityProfiles()
	}
	var rootPath by remember { mutableStateOf<String?>(null) }
	var qualityId by remember { mutableStateOf<Long?>(null) }
	var future by remember { mutableStateOf(false) }
	var optionsPage by remember { mutableStateOf(false) }

	LaunchedEffect(step.album) {
		val album = step.album ?: return@LaunchedEffect
		flow.guard {
			val upc = album.deezerId?.let { deezer.getAlbumUpc(it) }
			val group = upc?.let { musicBrainz.resolveReleaseGroupByUpc(it) }
				?: musicBrainz.searchReleaseGroup(album.title, album.artist)
				?: throw NotInLidarrException(musicBrainzSearchUrl("release_group", "${album.title} ${album.artist}"))
			val existing = flow.artistExistsFor(listOf(group.id))
			val pick = AlbumPick(
				group.id, album.title, dotted(album.artist, group.primaryType, album.year?.toString()),
				album.coverUrl ?: coverArtUrl(group.id)
			)
			flow.step = AddStep.Albums(album.artist, listOf(pick), existing)
		}
	}
	// first time: the first root folder and its default quality
	LaunchedEffect(options) {
		val (roots, qualities) = options ?: return@LaunchedEffect
		val root = roots.firstOrNull { it.path == preferences.lidarrLastRootFolder } ?: roots.firstOrNull()
		rootPath = root?.path
		val lastQuality = preferences.lidarrLastQualityProfileId.toLong()
		qualityId = lastQuality.takeIf { root?.path == preferences.lidarrLastRootFolder && qualities.any { it.id == lastQuality } }
			?: root?.defaultQualityProfileId
	}
	val roots = options?.first.orEmpty()
	val qualities = options?.second.orEmpty()
	val root = roots.firstOrNull { it.path == rootPath }
	val quality = qualities.firstOrNull { it.id == qualityId }

	ModalBottomSheet(
		onDismissRequest = { flow.step = null },
		onBack = if (optionsPage) ({ optionsPage = false }) else null
	) {
		if (optionsPage) {
			DownloadOptionsPage(
				roots, qualities, root, quality,
				onRoot = { rootPath = it.path; qualityId = it.defaultQualityProfileId },
				onQuality = { qualityId = it.id },
				onBack = { optionsPage = false }
			)
			return@ModalBottomSheet
		}
		Text(
			stringResource(Res.string.action_add_to_lidarr),
			Modifier.padding(horizontal = 24.dp),
			style = MaterialTheme.typography.headlineSmall
		)
		val picks = step.picks
		if (picks == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
		else Column(Modifier.weight(1f, fill = false).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
			picks.forEach { pick ->
				Row(
					Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(16.dp)
				) {
					DiscoverArt(pick.coverUrl, 56.dp)
					Column {
						Text(pick.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
						Text(
							pick.subtitle,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
			Text(
				if (step.existingArtist) stringResource(Res.string.info_artist_in_lidarr, step.artistName)
				else pluralStringResource(Res.plurals.info_artist_new_in_lidarr, picks.size, picks.size, step.artistName),
				Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			if (!step.existingArtist) Column(
				Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
				verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.SegmentedGap)
			) {
				SegmentedListItem(
					onClick = { future = !future },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2),
					supportingContent = { Text(stringResource(Res.string.subtitle_future_releases, step.artistName)) },
					trailingContent = { Switch(checked = future, onCheckedChange = { future = it }) }
				) { Text(stringResource(Res.string.option_future_releases)) }
				SegmentedListItem(
					onClick = { optionsPage = true },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
					enabled = root != null,
					leadingContent = { Icon(Icons.Outlined.Settings, null) },
					supportingContent = { Text(dotted(root?.path, quality?.name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
					trailingContent = { Icon(Icons.Outlined.ChevronForward, null) }
				) { Text(stringResource(Res.string.title_download_options)) }
			}
		}
		SheetButtons(
			confirm = stringResource(Res.string.action_add_and_search),
			enabled = picks != null && (step.existingArtist || (root != null && quality != null)),
			busy = false,
			onCancel = { flow.step = null }
		) {
			if (step.existingArtist || root == null || quality == null) {
				flow.addAlbums(picks.orEmpty(), false, null, 0L)
				return@SheetButtons
			}
			preferences.lidarrLastRootFolder = root.path
			preferences.lidarrLastQualityProfileId = quality.id.toInt()
			flow.addAlbums(picks.orEmpty(), future, root, quality.id)
		}
	}
}

/** The add-album sheet's sub-page: root folder (with free space) and quality profile. */
@Composable
private fun ColumnScope.DownloadOptionsPage(
	roots: List<LidarrRootFolder>,
	qualities: List<LidarrQualityProfile>,
	root: LidarrRootFolder?,
	quality: LidarrQualityProfile?,
	onRoot: (LidarrRootFolder) -> Unit,
	onQuality: (LidarrQualityProfile) -> Unit,
	onBack: () -> Unit
) {
	Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(Res.string.action_back)) }
		Text(stringResource(Res.string.title_download_options), style = MaterialTheme.typography.titleLarge)
	}
	Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
		SectionLabel(Res.string.title_root_folder)
		roots.forEach {
			RadioRow(
				it.id == root?.id, it.path,
				dotted(it.name, stringResource(Res.string.label_free_space, it.freeSpace.toFileSize()))
			) { onRoot(it) }
		}
		SectionLabel(Res.string.title_quality)
		qualities.forEach {
			RadioRow(
				it.id == quality?.id, it.name,
				root?.takeIf { r -> r.defaultQualityProfileId == it.id }
					?.let { r -> stringResource(Res.string.label_default_for, r.path) }
			) { onQuality(it) }
		}
		Text(
			stringResource(Res.string.info_download_options),
			Modifier.padding(24.dp),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
	Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.End) {
		Button(onClick = onBack) { Text(stringResource(Res.string.action_done)) }
	}
}

/** A titled group label inside the add sheets ("Root folder", "Lidarr options"). */
@Composable
internal fun SectionLabel(title: StringResource) {
	Text(
		stringResource(title),
		Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary
	)
}

/** A radio choice row; [highlight] tints the selected one, like the download options sheet. */
@Composable
internal fun RadioRow(
	selected: Boolean,
	title: String,
	subtitle: String?,
	highlight: Boolean = true,
	onClick: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(if (selected && highlight) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
			.selectable(selected, role = Role.RadioButton, onClick = onClick)
			.padding(horizontal = 24.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		RadioButton(selected = selected, onClick = null)
		Column {
			Text(title, style = MaterialTheme.typography.bodyLarge)
			subtitle?.let {
				Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}

/** An info line in a tinted box, like the add sheets' "already in Lidarr" notice. */
@Composable
internal fun Notice(text: String) {
	Surface(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
		shape = MaterialTheme.shapes.medium,
		color = MaterialTheme.colorScheme.surfaceContainerHigh
	) {
		Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
			Text(text, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

/** The add sheets' Cancel and confirm buttons, at the end; a spinner while [busy]. */
@Composable
internal fun SheetButtons(
	confirm: String,
	enabled: Boolean,
	busy: Boolean,
	onCancel: () -> Unit,
	onConfirm: () -> Unit
) {
	Row(
		Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (busy) CircularProgressIndicator(Modifier.size(24.dp))
		TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
		Button(onClick = onConfirm, enabled = enabled && !busy) { Text(confirm) }
	}
}

/** "Not in Lidarr yet" (with a way to MusicBrainz) or "metadata server is down". */
@Composable
private fun LidarrLookupDialog(
	title: StringResource,
	paragraphs: List<StringResource>,
	musicBrainzUrl: String?,
	onDismissRequest: () -> Unit
) {
	val linkManager = koinInject<LinkManager>()
	FormDialog(
		width = 340.dp,
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Schedule, null) },
		title = { Text(stringResource(title)) },
		buttons = {
			if (musicBrainzUrl != null) TextButton(onClick = {
				linkManager.openLink(musicBrainzUrl)
				onDismissRequest()
			}) { Text(stringResource(Res.string.action_open_musicbrainz)) }
			TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_ok)) }
		}
	) {
		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			paragraphs.forEach { Text(stringResource(it)) }
		}
	}
}
