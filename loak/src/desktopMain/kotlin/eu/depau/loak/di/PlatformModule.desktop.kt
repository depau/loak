package eu.depau.loak.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.domain.manager.AppIconManager
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.LogManager
import eu.depau.loak.domain.manager.MediaControlManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.shared.DesktopMediaPlayerViewModel
import eu.depau.loak.shared.MediaPlayerViewModel
import coil3.PlatformContext as CoilPlatformContext
import java.io.File
import java.nio.file.Files
import java.util.Properties

val desktopDataDir: File by lazy {
	val dir = File(
		System.getProperty("user.home"),
		when {
			Os.isWindows -> "AppData/Local/Loak"
			Os.isMac -> "Library/Application Support/Loak"
			else -> ".local/share/Loak"
		}
	)
	Files.createDirectories(dir.toPath())
	dir
}

actual val platformModule = module {
	single { PlatformType.Desktop }

	single<Settings> {
		val propertiesFile = File(desktopDataDir, "settings.properties")
		val properties = Properties().apply {
			if (propertiesFile.exists()) {
				runCatching { propertiesFile.inputStream().use { load(it) } }
			}
		}
		PropertiesSettings(properties) { props ->
			runCatching { propertiesFile.outputStream().use { props.store(it, null) } }
		}
	}
	single<CacheDatabase> {
		Room
			.databaseBuilder<CacheDatabase>(File(desktopDataDir, "cache.db").absolutePath)
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<DownloadDatabase> {
		Room
			.databaseBuilder<DownloadDatabase>(File(desktopDataDir, "downloads.db").absolutePath)
			.setDriver(BundledSQLiteDriver())
			.fallbackToDestructiveMigration(true)
			.build()
	}

	single<MediaPlayerViewModel> {
		DesktopMediaPlayerViewModel(
			stateRepository = get(),
			songRepository = get(),
			connectivityManager = get(),
			downloadManager = get(),
			preferenceManager = get(),
			queueSyncManager = get(),
			syncManager = get(),
			sessionManager = get(),
			mediaControlManager = get(),
			snackBarManager = get()
		)
	}

	single<CoilPlatformContext> { CoilPlatformContext.INSTANCE }
	singleOf(::MediaControlManager)
	singleOf(::ShareManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::AppIconManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::AudioGainManager)
}

private object Os {
	val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
	val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)
}
