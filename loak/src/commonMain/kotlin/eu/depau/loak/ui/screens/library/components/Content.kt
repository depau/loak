package eu.depau.loak.ui.screens.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_sort_frequent
import eu.depau.loak.generated.resources.option_sort_newest
import eu.depau.loak.generated.resources.option_sort_random
import eu.depau.loak.generated.resources.option_sort_recent
import eu.depau.loak.generated.resources.option_sort_starred
import eu.depau.loak.generated.resources.title_artists
import eu.depau.loak.generated.resources.title_genres
import eu.depau.loak.generated.resources.title_playlists
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainGenre
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.LibraryAdd
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.layouts.horizontalSection
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.ui.screens.album.components.AlbumListScreenGridItem
import eu.depau.loak.ui.screens.artist.ArtistListScreenGridItem
import eu.depau.loak.ui.screens.genre.components.GenreListScreenCard
import eu.depau.loak.ui.screens.playlist.components.PlaylistListScreenGridItem
import eu.depau.loak.ui.util.withoutTop
import eu.depau.loak.ui.util.loakAnimateItem

@Composable
fun LibraryScreenContent(
	state: LazyGridState,
	scrollBehavior: TopAppBarScrollBehavior,
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,

	// albums
	albumsState: UiState<ImmutableList<DomainAlbum>>,
	selectedAlbum: DomainAlbum?,
	selectedAlbumIsStarred: Boolean,
	selectedAlbumRating: Int,
	onSelectAlbum: (DomainAlbum) -> Unit,
	onClearAlbumSelection: () -> Unit,
	onStarSelectedAlbum: (Boolean) -> Unit,
	onRateSelectedAlbum: (Int) -> Unit,
	onPlayAlbumNext: () -> Unit,
	onAddAlbumToQueue: () -> Unit,

	// artists
	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,

	// playlists
	playlistsState: UiState<ImmutableList<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	onSelectPlaylist: (DomainPlaylist) -> Unit,
	onClearPlaylistSelection: () -> Unit,
	onDeletePlaylist: (String) -> Unit,
	onPlayPlaylistNext: () -> Unit,
	onAddPlaylistToQueue: () -> Unit,

	// genres
	genresState: UiState<ImmutableList<DomainGenre>>
) {
	// wider windows: the four shortcuts fit on one row
	val columns = if (LocalPlatformContext.current.isExpanded()) 4 else 2
	LazyVerticalGrid(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		columns = GridCells.Fixed(columns),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
		horizontalArrangement = Arrangement.spacedBy(5.dp),
		state = state
	) {
		libraryScreenOverviewButton(
			icon = Icons.Outlined.LibraryAdd,
			label = Res.string.option_sort_newest,
			destination = Screen.AlbumList(true, DomainAlbumListType.Newest),
			start = 0 % columns == 0,
			end = 0 % columns == columns - 1
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Shuffle,
			label = Res.string.option_sort_random,
			destination = Screen.AlbumList(true, DomainAlbumListType.Random),
			start = 1 % columns == 0,
			end = 1 % columns == columns - 1
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Star,
			label = Res.string.option_sort_starred,
			destination = Screen.Starred(),
			start = 2 % columns == 0,
			end = 2 % columns == columns - 1
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.History,
			label = Res.string.option_sort_frequent,
			destination = Screen.AlbumList(true, DomainAlbumListType.Frequent),
			start = 3 % columns == 0,
			end = 3 % columns == columns - 1
		)

		horizontalSection(
			title = Res.string.option_sort_recent,
			destination = Screen.AlbumList(true, DomainAlbumListType.Recent),
			state = albumsState,
			key = { it.id },
			seeAll = true
		) { album ->
			AlbumListScreenGridItem(
				modifier = loakAnimateItem().width(150.dp),
				tab = "library",
				album = album,
				selected = album == selectedAlbum,
				starred = selectedAlbumIsStarred,
				onSelect = { onSelectAlbum(album) },
				onDeselect = { onClearAlbumSelection() },
				onSetStarred = { onStarSelectedAlbum(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayAlbumNext,
				onAddToQueue = onAddAlbumToQueue,
				rating = selectedAlbumRating,
				onSetRating = onRateSelectedAlbum
			)
		}

		horizontalSection(
			title = Res.string.title_playlists,
			destination = Screen.PlaylistList(true),
			state = playlistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenGridItem(
				modifier = loakAnimateItem().width(150.dp),
				tab = "library",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_artists,
			destination = Screen.ArtistList(true),
			state = artistsState,
			key = { it.id },
			seeAll = true
		) { artist ->
			ArtistListScreenGridItem(
				modifier = loakAnimateItem().width(150.dp),
				tab = "library",
				artist = artist,
				selected = artist == selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				starred = selectedArtistIsStarred,
				onSelect = { onSelectArtist(artist) },
				onDeselect = { onClearArtistSelection() },
				onSetStarred = { onStarSelectedArtist(it) },
				onPlayNext = onPlayArtistNext,
				onAddToQueue = onAddArtistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_genres,
			destination = Screen.GenreList(true),
			state = genresState,
			key = { it.name },
			seeAll = true
		) { genreWithAlbums ->
			// as wide as a card in the 2-column genre grid, so both look the same
			GenreListScreenCard(modifier = Modifier.width(180.dp), genre = genreWithAlbums)
		}
	}
}
