package eu.depau.loak.di

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Startup smoke test: `initKoin()` must run to completion — including the eager
 * singleton graph (`createEagerInstances`) — without a missing definition or a
 * re-entrant construction cycle.
 *
 * Reproduces the Android Application.onCreate → initKoin crash path (LOAK-1Q):
 * `singleOf(::SessionManager)` / `singleOf(::LyricsRepository)` force Koin to resolve
 * every constructor param, including the optional `HttpClientEngine? = null` test seam
 * that is never DI-registered, throwing `NoDefinitionFoundException` at startup.
 * Those registrations now use explicit `single {}` factories that omit the defaulted
 * engine param, so startup resolves nothing extra.
 *
 * Also resolves the Explore view model factory — `viewModelOf(::ExploreViewModel)` hit
 * the same trap via its `CoroutineDispatcher`/library-loader test seams (LOAK-1W).
 */
class KoinStartupTest {
	@Test
	fun initKoin_buildsFullEagerGraph() {
		val koin = initKoin {}
		try {
			assertNotNull(koin.koin.get<eu.depau.loak.domain.manager.SessionManager>())
			assertNotNull(koin.koin.get<eu.depau.loak.domain.manager.DownloadManager>())
			assertNotNull(koin.koin.get<eu.depau.loak.ui.screens.explore.ExploreViewModel>())
		} finally {
			koin.close()
		}
	}
}
