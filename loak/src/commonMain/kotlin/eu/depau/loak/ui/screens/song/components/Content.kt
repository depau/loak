package eu.depau.loak.ui.screens.song.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import kotlinx.collections.immutable.ImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_songs
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.common.libraryEmptyLabel
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.util.loakAnimateItem
import eu.depau.loak.ui.components.common.SongRow
import androidx.compose.foundation.layout.fillMaxWidth

fun LazyListScope.songListScreenContent(
	state: UiState<ImmutableList<DomainSong>>,
	allDownloads: List<DownloadEntity>,
	onPlaySong: (DomainSong) -> Unit
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data) { song ->
			val download = allDownloads.find { it.songId == song.id }
			SongRow(
				modifier = loakAnimateItem().fillMaxWidth(),
				song = song,
				onClick = { onPlaySong(song) },
				download = download
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				// TODO
			}

			else -> {
				item {
					ContentUnavailable(
						icon = Icons.Outlined.Note,
						label = libraryEmptyLabel(stringResource(Res.string.info_no_songs))
					)
				}
			}
		}
	}
}
