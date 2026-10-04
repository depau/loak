package eu.depau.loak.ui.screens.genre.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_genres
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainGenre
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.common.libraryEmptyLabel
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.util.loakAnimateItem

fun LazyGridScope.genreListScreenContent(
	state: UiState<List<DomainGenre>>
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data, { it.name }) { genre ->
			GenreListScreenCard(
				modifier = loakAnimateItem(),
				genre = genre
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> items(10) {
				GenreListScreenCardPlaceholder()
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.Genre,
						label = libraryEmptyLabel(stringResource(Res.string.info_no_genres))
					)
				}
			}
		}
	}
}
