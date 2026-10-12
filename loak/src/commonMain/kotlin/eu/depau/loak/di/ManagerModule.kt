package eu.depau.loak.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import eu.depau.loak.util.IoDispatcher
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EqualiserManager
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
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.StorageManager
import eu.depau.loak.domain.manager.SyncManager

val managerModule = module {
	singleOf(::SleepTimerManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	// eager: resumes queued downloads on startup
	singleOf(::DownloadManager) { createdAtStart() }
	singleOf(::SessionManager)
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
	singleOf(::NetworkStatsManager)
	// eager: sweeps leftovers and evicts on startup
	single(createdAtStart = true) {
		AudioStore(
			get<StorageManager>().audioStoreDir(), get(), get(), get<SessionManager>().serverKey
		)
	}
	singleOf(::AudioFetcher)
}
