package eu.depau.loak.di

import androidx.media3.common.util.UnstableApi
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.data.database.migrationPolicy
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.LogManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.VolumeProvider
import eu.depau.loak.exoplayer.AudioGainProcessor
import eu.depau.loak.shared.AndroidMediaPlayerViewModel
import eu.depau.loak.shared.MediaPlayerViewModel

@UnstableApi
actual val platformModule = module {
	single { PlatformType.Android }
	single<CacheDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("cache.db")
			.absolutePath
		Room
			.databaseBuilder<CacheDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 21)
			.build()
	}

	single<DownloadDatabase> {
		val dbPath = androidApplication()
			.getDatabasePath("downloads.db")
			.absolutePath
		Room
			.databaseBuilder<DownloadDatabase>(get(), dbPath)
			.setDriver(BundledSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 3)
			.build()
	}

	single<MediaPlayerViewModel> {
		AndroidMediaPlayerViewModel(
			application = androidApplication(),
			stateRepository = get(),
			songRepository = get(),
			albumDao = get(),
			downloadManager = get(),
			connectivityManager = get(),
			sessionManager = get(),
			preferenceManager = get(),
			queueSyncManager = get(),
			snackBarManager = get(),
			audioGainManager = get(),
		)
	}

	singleOf(::ShareManager)
	singleOf(::ExportManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::AudioGainManager)
	singleOf(::AudioGainProcessor)
	single<VolumeProvider> { VolumeProvider(androidApplication()) }
}
