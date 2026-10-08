package eu.depau.loak.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.data.database.migrationPolicy
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.LogManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.VolumeProvider
import eu.depau.loak.shared.IOSMediaPlayerViewModel
import eu.depau.loak.shared.MediaPlayerViewModel
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import coil3.PlatformContext as CoilPlatformContext

actual val platformModule = module {
	single { PlatformType.IOS }
	single<CacheDatabase> {
		val dbPath = documentDirectory() + "/cache.db"
		Room
			.databaseBuilder<CacheDatabase>(dbPath)
			.setDriver(BundledSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 21)
			.build()
	}

	single<DownloadDatabase> {
		val dbPath = documentDirectory() + "/downloads.db"
		Room
			.databaseBuilder<DownloadDatabase>(dbPath)
			.setDriver(BundledSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 3)
			.build()
	}

	single<MediaPlayerViewModel> {
		IOSMediaPlayerViewModel(
			stateRepository = get(),
			songRepository = get(),
			downloadManager = get(),
			connectivityManager = get(),
			syncManager = get(),
			sessionManager = get(),
			preferenceManager = get(),
			queueSyncManager = get(),
			snackBarManager = get()
		)
	}

	singleOf(::ShareManager)
	singleOf(::ExportManager)
	single<CoilPlatformContext> { CoilPlatformContext.INSTANCE }
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::VolumeProvider)
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
	val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
		directory = NSDocumentDirectory,
		inDomain = NSUserDomainMask,
		appropriateForURL = null,
		create = false,
		error = null,
	)
	return requireNotNull(documentDirectory?.path)
}
