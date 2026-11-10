package eu.depau.loak.di

import androidx.room3.Room
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.data.database.migrationPolicy
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.discover.DesktopPreviewPlayer
import eu.depau.loak.domain.discover.PreviewPlayer
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.LogManager
import eu.depau.loak.domain.manager.MediaControlManager
import eu.depau.loak.domain.manager.NewAlbumsNotifier
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.VolumeProvider
import eu.depau.loak.shared.DesktopMediaPlayerViewModel
import eu.depau.loak.shared.MediaPlayerViewModel
import coil3.PlatformContext as CoilPlatformContext
import java.io.File
import java.nio.file.Files
import java.util.Properties

val desktopDataDir: File by lazy {
	val home = System.getProperty("user.home")
	val dir = when {
		Os.isWindows -> File(home, "AppData/Local/Loak")
		Os.isMac -> File(home, "Library/Application Support/Loak")
		else -> {
			val legacy = File(home, ".local/share/Loak")
			// Flatpak redirects XDG_DATA_HOME into the app's sandbox; ~/.local/share is a
			// throwaway tmpfs there. Keep an existing pre-XDG install where it is.
			val target = File(
				System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
					?: File(home, ".local/share").path,
				"Loak",
			)
			if (!target.exists() && legacy.exists()) legacy else target
		}
	}
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
			// one writer at a time: the player state saves from a background thread, and two
			// streams truncating the same file would interleave
			synchronized(props) {
				runCatching { propertiesFile.outputStream().use { props.store(it, null) } }
			}
		}
	}
	single<CacheDatabase> {
		Room
			.databaseBuilder<CacheDatabase>(File(desktopDataDir, "cache.db").absolutePath)
			.setDriver(JdbcSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 21)
			.build()
	}

	single<DownloadDatabase> {
		Room
			.databaseBuilder<DownloadDatabase>(File(desktopDataDir, "downloads.db").absolutePath)
			.setDriver(JdbcSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 3)
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
	singleOf(::ExportManager)
	singleOf(::StorageManager)
	singleOf(::ConnectivityManager)
	singleOf(::LogManager)
	singleOf(::PermissionManager)
	singleOf(::LinkManager)
	singleOf(::AudioGainManager)
	single<PreviewPlayer> { DesktopPreviewPlayer() }
	singleOf(::VolumeProvider)
	singleOf(::NewAlbumsNotifier)
}

private object Os {
	val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
	val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)
}
