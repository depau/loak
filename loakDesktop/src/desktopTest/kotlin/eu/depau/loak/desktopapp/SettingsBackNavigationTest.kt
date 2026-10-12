package eu.depau.loak.desktopapp

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.Settings
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.SettingsAppearanceScreen
import eu.depau.loak.ui.theme.LoakTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * CF-06 at the UI layer: a settings detail page (Appearance) shows the back arrow and it
 * pops the detail page off the back stack, returning to the parent settings screen.
 * The desktop += settings detail back action regressed once (Oct 2026), so the arrow
 * staying put — not just "going back" via an app-wide gesture — is what this locks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class SettingsBackNavigationTest {

	@AfterTest
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun backArrowOnSettingsDetailPopsBackToSettingsRoot() = runComposeUiTest {
		startKoin {
			allowOverride(true)
			modules(
				module {
					single { PreferenceManager(Settings()) }
				}
			)
		}

		val backStack = NavBackStack<NavKey>(Screen.Settings.Root, Screen.Settings.Appearance)

		setContent {
			val backStack = remember { backStack }
			val platformContext = remember {
				object : PlatformContext {
					override val name = "desktop-test"
					override val appVersion = "test"
					override val buildVariant = "test"
					override val sizeClass = WindowSizeClass.calculateFromSize(DpSize(900.dp, 700.dp))
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
					SettingsAppearanceScreen()
				}
			}
		}

		// the detail page is on screen, then its back arrow pops it, returning to Settings root
		assertEquals<List<NavKey>>(listOf(Screen.Settings.Root, Screen.Settings.Appearance), backStack.toList())
		onNodeWithContentDescription("Navigate up").assertIsDisplayed().performClick()
		waitForIdle()
		assertEquals<List<NavKey>>(listOf(Screen.Settings.Root), backStack.toList())
	}
}
