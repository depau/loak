package eu.depau.loak.di

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.w3c.dom.Worker
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.data.database.migrationPolicy
import eu.depau.loak.domain.manager.AppIconManager
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.LogManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.VolumeProvider
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.shared.WebMediaPlayerViewModel
import eu.depau.loak.web.createSqliteWorker
import coil3.PlatformContext as CoilPlatformContext


actual val platformModule = module {
	single { PlatformType.Web }

	single<CacheDatabase> { webDatabase<CacheDatabase>("cache.db", firstMigratedVersion = 21) }

	single<DownloadDatabase> { webDatabase<DownloadDatabase>("downloads.db", firstMigratedVersion = 3) }

	single<MediaPlayerViewModel> {
		WebMediaPlayerViewModel(
			stateRepository = get(),
			songRepository = get(),
			connectivityManager = get(),
			downloadManager = get(),
			preferenceManager = get(),
			queueSyncManager = get(),
			syncManager = get(),
			sessionManager = get(),
			snackBarManager = get()
		)
	}

	singleOf(::ShareManager)
	singleOf(::ExportManager)
	single<CoilPlatformContext> { CoilPlatformContext.INSTANCE }
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::AppIconManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::AudioGainManager)
	singleOf(::VolumeProvider)
}

private inline fun <reified T : RoomDatabase> webDatabase(
	name: String,
	firstMigratedVersion: Int
): T =
	Room.databaseBuilder<T>(name)
		.setDriver(WebWorkerSQLiteDriver(createSqliteWorker()))
		.migrationPolicy(firstMigratedVersion)
		.build()
