package eu.depau.loak.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import eu.depau.loak.util.IoDispatcher
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EqualiserManager
import eu.depau.loak.domain.history.ListeningHistory
import eu.depau.loak.domain.history.LocalHistory
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.SleepTimerManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.SyncManager

val managerModule = module {
	single { SleepTimerManager(get<MediaPlayerViewModel>()) }
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}.onOptions { onClose { it?.close() } }
	// eager: resumes queued downloads on startup
	singleOf(::DownloadManager) { createdAtStart() }
		.onOptions { onClose { it?.close() } }
	// `singleOf` here would force `get<HttpClientEngine>()`: SessionManager's optional
	// HTTP-engine test seam (`HttpClientEngine? = null`) isn't DI-registered, so eager
	// resolution blew up LOAK-1Q. An explicit factory that omits the defaulted param
	// resolves nothing extra — production uses Ktor's platform engine, and tests
	// construct SessionManager directly with a MockEngine.
	single { SessionManager(get(), get(), get()) }
	singleOf(::NavidromeManager)
	single(createdAtStart = true) {
		AudioMuseManager(get()).also { manager -> CoroutineScope(IoDispatcher).launch { manager.start() } }
	}
	single(createdAtStart = true) {
		LidarrManager(get(), get(), get()).also { manager -> CoroutineScope(IoDispatcher).launch { manager.start() } }
	}
	singleOf(::LibraryMatcher)
	singleOf(::PreferenceManager)
	// Deezer's search API, no auth: queries go straight to api.deezer.com
	single { DeezerClient() }
	// MusicBrainz's identity API: rate-limited, cached; also used for Lidarr adds
	single { MusicBrainzClient() }
	// ListenBrainz's Labs API: similar artists, no auth
	single { ListenBrainzClient() }
	// previews: mediaPlayer resolves lazily to dodge the VM↔manager cycle
	single { PreviewManager(get(), get(), { get() }) }
	singleOf(::SnackBarManager)
	singleOf(::LoginManager)
	singleOf(::EqualiserManager)
	singleOf(::QueueSyncManager)
	singleOf(::PlayLogManager)
	// What Quick picks rank on; the remote sources (ListenBrainz, Koito, Last.fm) add to it
	single<ListeningHistory> { LocalHistory(get()) }
	singleOf(::NetworkStatsManager)
	// eager: sweeps leftovers and evicts on startup
	single(createdAtStart = true) {
		AudioStore(
			get<StorageManager>().audioStoreDir(), get(), get(), get<SessionManager>().serverKey
		)
	}.onOptions { onClose { it?.close() } }
	singleOf(::AudioFetcher)
}
