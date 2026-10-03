package eu.depau.loak.di

import eu.depau.loak.ui.screens.explore.ExploreViewModel
import eu.depau.loak.ui.screens.alchemy.AlchemyViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.ui.components.dialogs.DeletionViewModel
import eu.depau.loak.ui.components.sheets.ChangelogViewModel
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.home.viewmodels.HomeViewModel
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistDetailViewModel
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistListViewModel
import eu.depau.loak.ui.screens.collection.viewmodels.CollectionDetailViewModel
import eu.depau.loak.ui.screens.genre.viewmodels.GenreListViewModel
import eu.depau.loak.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistCreateDialogViewModel
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistListViewModel
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel
import eu.depau.loak.ui.screens.queue.viewmodels.QueueViewModel
import eu.depau.loak.ui.screens.radio.viewmodels.RadioCreateDialogViewModel
import eu.depau.loak.ui.screens.radio.viewmodels.RadioListViewModel
import eu.depau.loak.ui.screens.search.viewmodels.SearchViewModel
import eu.depau.loak.ui.screens.settings.viewmodels.LyricsPriorityViewModel
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel
import eu.depau.loak.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import eu.depau.loak.ui.screens.share.viewmodels.ShareDialogViewModel
import eu.depau.loak.ui.screens.share.viewmodels.ShareListViewModel
import eu.depau.loak.ui.screens.song.viewmodels.SongDetailViewModel
import eu.depau.loak.ui.screens.song.viewmodels.SongListViewModel
import eu.depau.loak.ui.viewmodel.RootViewModel

val viewModelModule = module {
	// a song, artist, playlist, mood or radio to start from, or nothing
	viewModel { params -> AlchemyViewModel(params.getOrNull(), get(), get()) }

	// no parameter: Home; a genre name: that genre's page
	viewModel { params ->
		HomeViewModel(
			fixedGenre = params.getOrNull(),
			repository = get(),
			songRepository = get(),
			player = get(),
			snackBarManager = get(),
			sessionManager = get(),
			syncManager = get()
		)
	}

	viewModel { (artistId: String) ->
		ArtistDetailViewModel(
			artistId = artistId,
			repository = get(),
			artistRepository = get(),
			songRepository = get(),
			albumRepository = get(),
			artistDao = get(),
			albumDao = get(),
			downloadManager = get(),
			snackBarManager = get(),
			connectivityManager = get()
		)
	}

	viewModel { (song: DomainSong?) ->
		LyricsScreenViewModel(
			song = song,
			repository = get()
		)
	}

	viewModel { (songs: List<DomainSong>, playlistToExclude: String?) ->
		PlaylistUpdateDialogViewModel(
			songs = songs,
			playlistToExclude = playlistToExclude,
			sessionManager = get(),
			snackBarManager = get(),
			preferenceManager = get()
		)
	}

	viewModel { params ->
		AlbumListViewModel(
			initialListType = params.getOrNull<DomainAlbumListType>(),
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			sessionManager = get(),
			preferenceManager = get(),
			syncManager = get()
		)
	}
	viewModel { params ->
		SongListViewModel(
			initialListType = params.getOrNull<DomainSongListType>(),
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			downloadManager = get(),
			sessionManager = get(),
			preferenceManager = get(),
			connectivityManager = get(),
			syncManager = get()
		)
	}
	viewModel { params ->
		ArtistListViewModel(
			initialListType = params.getOrNull<DomainArtistListType>(),
			initialFilters = params.getOrNull<Set<DomainFilter>>(),
			repository = get(),
			albumDao = get(),
			sessionManager = get(),
			preferenceManager = get(),
			connectivityManager = get()
		)
	}
	viewModelOf(::SearchViewModel)
	viewModelOf(::ExploreViewModel)
	viewModelOf(::GenreListViewModel)
	viewModelOf(::RadioListViewModel)
	viewModelOf(::RadioCreateDialogViewModel)
	viewModelOf(::PlaylistListViewModel)
	viewModelOf(::QueueViewModel)
	viewModelOf(::ShareListViewModel)
	viewModelOf(::DeletionViewModel)
	viewModelOf(::ShareDialogViewModel)
	viewModel { (songs: List<DomainSong>) ->
		PlaylistCreateDialogViewModel(
			songs = songs,
			playlistDao = get(),
			sessionManager = get(),
			snackBarManager = get()
		)
	}
	viewModel { params ->
		CollectionDetailViewModel(
			collectionId = params.get(),
			repository = get(),
			songRepository = get(),
			albumRepository = get(),
			downloadManager = get(),
			sessionManager = get(),
			snackBarManager = get(),
			syncManager = get(),
			connectivityManager = get()
		)
	}
	viewModelOf(::SongDetailViewModel)
	viewModelOf(::SettingsDataStorageViewModel)
	viewModelOf(::ChangelogViewModel)
	viewModel { params ->
		NowPlayingViewModel(
			player = params.get(),
			songRepository = get()
		)
	}
	viewModelOf(::NavtabsViewModel)
	viewModelOf(::LyricsPriorityViewModel)
	single { RootViewModel() }
}
