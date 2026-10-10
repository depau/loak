package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.LidarrRootFolder
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_artist
import eu.depau.loak.generated.resources.action_more_monitor_options
import eu.depau.loak.generated.resources.count_albums_eps_on_musicbrainz
import eu.depau.loak.generated.resources.label_free_space
import eu.depau.loak.generated.resources.label_metadata_profile
import eu.depau.loak.generated.resources.label_new_releases
import eu.depau.loak.generated.resources.option_monitor_all
import eu.depau.loak.generated.resources.option_monitor_existing
import eu.depau.loak.generated.resources.option_monitor_first
import eu.depau.loak.generated.resources.option_monitor_future
import eu.depau.loak.generated.resources.option_monitor_latest
import eu.depau.loak.generated.resources.option_monitor_missing
import eu.depau.loak.generated.resources.option_monitor_none
import eu.depau.loak.generated.resources.option_new_releases_all
import eu.depau.loak.generated.resources.option_new_releases_new
import eu.depau.loak.generated.resources.option_new_releases_none
import eu.depau.loak.generated.resources.option_search_now
import eu.depau.loak.generated.resources.subtitle_monitor_none
import eu.depau.loak.generated.resources.subtitle_search_now
import eu.depau.loak.generated.resources.title_add_artist_named
import eu.depau.loak.generated.resources.title_download
import eu.depau.loak.generated.resources.title_lidarr_options
import eu.depau.loak.generated.resources.title_quality
import eu.depau.loak.generated.resources.title_root_folder
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import eu.depau.loak.util.toFileSize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState
import eu.depau.loak.generated.resources.info_artist_settings_kept

/** Lidarr's `addOptions.monitor` choices in plain words; the last two sit behind "More". */
private val monitorOptions = listOf(
	"all" to Res.string.option_monitor_all,
	"future" to Res.string.option_monitor_future,
	"latest" to Res.string.option_monitor_latest,
	"first" to Res.string.option_monitor_first,
	"none" to Res.string.option_monitor_none,
	"missing" to Res.string.option_monitor_missing,
	"existing" to Res.string.option_monitor_existing
)

/** Lidarr's `monitorNewItems` choices. */
private val newItemOptions = listOf(
	"all" to Res.string.option_new_releases_all,
	"new" to Res.string.option_new_releases_new,
	"none" to Res.string.option_new_releases_none
)

/**
 * The add-artist sheet: what to download (Lidarr's monitor options), then root folder, quality,
 * metadata profile and new-release monitoring, prefilled from the root folder's defaults, and
 * whether to search right away. Identifies the artist on MusicBrainz and Lidarr first.
 */
@Composable
internal fun AddArtistSheet(flow: LidarrAddFlow, artist: DiscoverArtist) {
	val lidarr = koinInject<LidarrManager>()
	val musicBrainz = koinInject<MusicBrainzClient>()
	val preferences = koinInject<PreferenceManager>()
	var mbid by remember { mutableStateOf<String?>(null) }
	var releaseCount by remember { mutableStateOf<Int?>(null) }
	val options by produceState<Triple<List<LidarrRootFolder>, List<Pair<Long, String>>, List<Pair<Long, String>>>?>(null) {
		value = Triple(
			lidarr.getRootFolders(),
			lidarr.getQualityProfiles().map { it.id to it.name },
			lidarr.getMetadataProfiles().map { it.id to it.name }
		)
	}
	var monitor by remember { mutableStateOf("all") }
	var showMore by remember { mutableStateOf(false) }
	var root by remember { mutableStateOf<LidarrRootFolder?>(null) }
	var qualityId by remember { mutableStateOf<Long?>(null) }
	var metadataId by remember { mutableStateOf<Long?>(null) }
	var newItems by remember { mutableStateOf("all") }
	var searchNow by remember { mutableStateOf(true) }
	val pickRoot = { folder: LidarrRootFolder ->
		root = folder
		qualityId = folder.defaultQualityProfileId
		metadataId = folder.defaultMetadataProfileId
		newItems = folder.defaultNewItemMonitorOption
	}

	LaunchedEffect(artist) {
		flow.guard {
			val id = artist.mbid ?: musicBrainz.searchArtist(artist.name)?.id?.takeIf { it.isNotEmpty() }
				?: throw NotInLidarrException(musicBrainzSearchUrl("artist", artist.name))
			lidarr.lookupArtistByMbId(id) ?: throw NotInLidarrException("https://musicbrainz.org/artist/$id")
			mbid = id
			releaseCount = musicBrainz.getArtistReleaseGroups(id).size
		}
	}
	LaunchedEffect(options) {
		val roots = options?.first ?: return@LaunchedEffect
		(roots.firstOrNull { it.path == preferences.lidarrLastRootFolder } ?: roots.firstOrNull())?.let(pickRoot)
	}
	val (roots, qualities, metadataProfiles) = options ?: Triple(emptyList(), emptyList(), emptyList())
	// an artist Lidarr has keeps its folder and profiles: adding just sets what it monitors
	val catalog by lidarr.catalog.collectAsState()
	val existing = mbid != null && catalog.artists.any { it.musicBrainzId == mbid }

	ModalBottomSheet(onDismissRequest = { flow.step = null }) {
		Row(
			Modifier.padding(horizontal = 24.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			DiscoverArt(artist.pictureUrl, 56.dp, CircleShape)
			Column {
				Text(stringResource(Res.string.title_add_artist_named, artist.name), style = MaterialTheme.typography.headlineSmall)
				releaseCount?.let {
					Text(
						pluralStringResource(Res.plurals.count_albums_eps_on_musicbrainz, it, it),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
		if (mbid == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
		Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
			SectionLabel(Res.string.title_download)
			monitorOptions.take(if (showMore) monitorOptions.size else 5).forEach { (value, label) ->
				RadioRow(
					monitor == value, stringResource(label),
					if (value == "none") stringResource(Res.string.subtitle_monitor_none) else null,
					highlight = false
				) { monitor = value }
			}
			if (!showMore) TextButton(onClick = { showMore = true }, Modifier.padding(horizontal = 12.dp)) {
				Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
				Text(stringResource(Res.string.action_more_monitor_options), Modifier.padding(start = 8.dp))
			}

			SectionLabel(Res.string.title_lidarr_options)
			if (existing) Notice(stringResource(Res.string.info_artist_settings_kept, artist.name))
			Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (!existing) OptionField(
					Res.string.title_root_folder, root?.path.orEmpty(), roots, { it.path }, pickRoot,
					suffix = root?.let { stringResource(Res.string.label_free_space, it.freeSpace.toFileSize()) }
				)
				if (!existing) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OptionField(
						Res.string.title_quality, qualities.firstOrNull { it.first == qualityId }?.second.orEmpty(),
						qualities, { it.second }, { qualityId = it.first }, Modifier.weight(1f)
					)
					OptionField(
						Res.string.label_metadata_profile, metadataProfiles.firstOrNull { it.first == metadataId }?.second.orEmpty(),
						metadataProfiles, { it.second }, { metadataId = it.first }, Modifier.weight(1f)
					)
				}
				val newItemLabels = newItemOptions.map { (value, label) -> value to stringResource(label) }
				OptionField(
					Res.string.label_new_releases, newItemLabels.firstOrNull { it.first == newItems }?.second.orEmpty(),
					newItemLabels, { it.second }, { newItems = it.first }
				)
			}
			Row(
				Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(Res.string.option_search_now), style = MaterialTheme.typography.bodyLarge)
					Text(
						stringResource(Res.string.subtitle_search_now),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Switch(checked = searchNow, onCheckedChange = { searchNow = it })
			}
		}
		val id = mbid
		val folder = root
		val quality = qualityId
		val metadata = metadataId
		SheetButtons(
			confirm = stringResource(Res.string.action_add_artist),
			enabled = id != null && (existing || (folder != null && quality != null && metadata != null)),
			busy = false,
			onCancel = { flow.step = null }
		) {
			if (id == null) return@SheetButtons
			if (existing) {
				flow.startArtistAdd(id, artist.name, monitor, searchNow, "", 0L, 0L, newItems)
				return@SheetButtons
			}
			if (folder == null || quality == null || metadata == null) return@SheetButtons
			preferences.lidarrLastRootFolder = folder.path
			flow.startArtistAdd(id, artist.name, monitor, searchNow, folder.path, quality, metadata, newItems)
		}
	}
}

/** A read-only dropdown field for one of the add-artist sheet's Lidarr options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionField(
	label: StringResource,
	value: String,
	options: List<T>,
	optionLabel: (T) -> String,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	suffix: String? = null
) {
	var expanded by remember { mutableStateOf(false) }
	ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
		OutlinedTextField(
			value = value,
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			label = { Text(stringResource(label)) },
			suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
			modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
		)
		ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			options.forEach {
				DropdownMenuItem(text = { Text(optionLabel(it)) }, onClick = { onSelect(it); expanded = false })
			}
		}
	}
}
