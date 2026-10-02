package eu.depau.loak.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.domain.repositories.AlbumRepository
import eu.depau.loak.domain.repositories.ArtistRepository
import eu.depau.loak.domain.repositories.CollectionRepository
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.domain.repositories.GenreRepository
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.domain.repositories.LyricsRepository
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.domain.repositories.RadioRepository
import eu.depau.loak.domain.repositories.SearchRepository
import eu.depau.loak.domain.repositories.ShareRepository
import eu.depau.loak.domain.repositories.SongRepository

val repositoryModule = module {
	singleOf(::AlbumRepository)
	singleOf(::ArtistRepository)
	singleOf(::DbRepository)
	singleOf(::GenreRepository)
	singleOf(::LyricsRepository)
	singleOf(::SearchRepository)
	singleOf(::ShareRepository)
	singleOf(::CollectionRepository)
	singleOf(::PlaylistRepository)
	singleOf(::SongRepository)
	singleOf(::RadioRepository)
	singleOf(::PlayerStateRepository)
	singleOf(::HomeRepository)
}
