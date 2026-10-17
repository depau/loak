package eu.depau.loak.desktopapp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.Settings
import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.login.pages.LoginScreenContent
import eu.depau.loak.ui.theme.LoakTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant

/**
 * CF-01 at the UI layer: the real [LoginScreenContent] mounts at phone size,
 * validates empty fields, and names rejected credentials instead of showing a
 * generic failure. Uses the seam'd SessionManager (Ktor MockEngine), so no
 * network or real library sync is involved; LoginManager's sync/repository
 * hooks are nullable in tests.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class LoginScreenUiTest {

	@AfterTest
	fun tearDown() {
		stopKoin()
	}

	/** A SessionManager whose fake Subsonic server rejects the credentials. */
	private fun fakeSessionManager(): SessionManager {
		val settings = Settings()
		val engine = MockEngine {
			respond(
				content = """{"subsonic-response":{"status":"failed","version":"1.16.1",
					"error":{"code":40,"message":"Wrong username or password"}}}""",
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}
		val prefs = PreferenceManager(settings)
		val conn = ConnectivityManager(prefs)
		val dao = object : NetworkStatsDao {
			override suspend fun add(hour: Long, metered: Boolean, category: TransferCategory, bytes: Long, requests: Long) {}
			override suspend fun getSince(since: Long) = emptyList<NetworkStatsEntity>()
			override suspend fun pruneTransfers(before: Long) {}
			override suspend fun insertSyncRun(run: SyncRunEntity) {}
			override suspend fun getSyncRuns(limit: Int) = emptyList<SyncRunEntity>()
			override suspend fun pruneSyncRuns(before: Instant) {}
		}
		val stats = NetworkStatsManager(dao, conn)
		return SessionManager(settings, prefs, stats, engine)
	}

	@Test
	fun validatesFieldsAndNamesRejectedCredentials() = runDesktopComposeUiTest(412, 892) {
		startKoin {
			allowOverride(true)
			modules(
				module {
					// seam'd session: login talks only to the fake server, never
					// a real instance (no network, no user data).
					single { fakeSessionManager() }
					single { PermissionManager() }
					single { PreferenceManager(Settings()) }
					single { LoginManager(repository = null, sessionManager = get(), syncManager = null) }
				}
			)
		}

		setContent {
			val backStack = remember { NavBackStack<NavKey>(Screen.Login) }
			val platformContext = remember {
				object : PlatformContext {
					override val name = "desktop-test"
					override val appVersion = "test"
					override val buildVariant = "test"
					override val sizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 892.dp))
					override val platformType = PlatformType.Desktop
					@Composable
					override fun systemColorScheme(isDark: Boolean) = null
				}
			}
			CompositionLocalProvider(
				LocalNavStack provides backStack,
				LocalPlatformContext provides platformContext
			) {
				LoakTheme {
					LoginScreenContent(PaddingValues(0.dp))
				}
			}
		}

		// the four login affordances are all present and interactive
		// ("Log in" is both the headline and the submit button)
		onNodeWithText("Instance URL").assertIsDisplayed().assertIsEnabled()
		onNodeWithText("Username").assertIsDisplayed().assertIsEnabled()
		onNodeWithText("Password").assertIsDisplayed().assertIsEnabled()
		onAllNodesWithText("Log in")[0].assertExists() // headline
		onAllNodesWithText("Log in")[1].assertIsDisplayed().assertIsEnabled() // submit
		onAllNodesWithText("Log in")[1].performClick()
		onNodeWithText("Instance URL").performTextInput("server.example")
		onNodeWithText("Username").performTextInput("demo")
		onNodeWithText("Password").performTextInput("wrong")
		onAllNodesWithText("Log in")[1].performClick()
		waitUntil(timeoutMillis = 5_000) {
			onAllNodesWithText("Wrong username or password.").fetchSemanticsNodes().isNotEmpty()
		}
		onNodeWithText("Wrong username or password.").assertIsDisplayed()
	}
}
