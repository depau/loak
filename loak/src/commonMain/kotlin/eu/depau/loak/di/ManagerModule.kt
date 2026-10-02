package eu.depau.loak.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EqualiserManager
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SleepTimerManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager

val managerModule = module {
	singleOf(::SleepTimerManager)
	single(createdAtStart = true) {
		SyncManager(get(), get(), get(), get(), get(), get()).apply {
			startPeriodicSync()
		}
	}
	singleOf(::DownloadManager)
	singleOf(::SessionManager)
	singleOf(::PreferenceManager)
	singleOf(::SnackBarManager)
	singleOf(::LoginManager)
	singleOf(::EqualiserManager)
	singleOf(::QueueSyncManager)
	singleOf(::PlayLogManager)
}
