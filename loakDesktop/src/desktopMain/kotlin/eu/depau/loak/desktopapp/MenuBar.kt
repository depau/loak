package eu.depau.loak.desktopapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.nucleusframework.menu.macos.NativeKey
import dev.nucleusframework.menu.macos.NativeKeyShortcut
import dev.nucleusframework.menu.macos.NativeMenuBar
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_about_app
import eu.depau.loak.generated.resources.action_back
import eu.depau.loak.generated.resources.action_forward
import eu.depau.loak.generated.resources.action_next_song
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.action_previous_song
import eu.depau.loak.generated.resources.action_quit_app
import eu.depau.loak.generated.resources.action_refresh
import eu.depau.loak.generated.resources.action_settings_menu
import eu.depau.loak.generated.resources.app_name
import eu.depau.loak.generated.resources.menu_controls
import eu.depau.loak.generated.resources.menu_go
import eu.depau.loak.generated.resources.menu_view
import eu.depau.loak.generated.resources.menu_window
import eu.depau.loak.generated.resources.title_search
import eu.depau.loak.ui.navigation.AppActions
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The macOS menu bar: the actions that have keyboard shortcuts, findable and labelled with
 * them. [NativeMenuBar] does nothing on other systems, where the shortcuts alone stay.
 */
@Composable
fun LoakMenuBar(actions: AppActions, quit: () -> Unit) {
	// items are clicked on the Swing thread: run the actions on the composition's instead
	val scope = rememberCoroutineScope()
	fun run(action: () -> Unit): () -> Unit = { scope.launch { action() } }

	val player by actions.playerState.collectAsState()
	val hasSong = player.currentSong != null
	val canRefresh = actions.canRefresh
	val canGoBack = actions.canGoBack
	val canGoForward = actions.canGoForward
	val inApp = actions.inApp

	val appName = stringResource(Res.string.app_name)
	val about = stringResource(Res.string.action_about_app, appName)
	val settings = stringResource(Res.string.action_settings_menu)
	val quitApp = stringResource(Res.string.action_quit_app, appName)
	val view = stringResource(Res.string.menu_view)
	val refresh = stringResource(Res.string.action_refresh)
	val go = stringResource(Res.string.menu_go)
	val back = stringResource(Res.string.action_back)
	val forward = stringResource(Res.string.action_forward)
	val search = stringResource(Res.string.title_search)
	val controls = stringResource(Res.string.menu_controls)
	val playPause =
		stringResource(if (player.isPaused) Res.string.action_play else Res.string.action_pause)
	val next = stringResource(Res.string.action_next_song)
	val previous = stringResource(Res.string.action_previous_song)
	val window = stringResource(Res.string.menu_window)

	NativeMenuBar {
		Menu(appName) {
			Item(about, enabled = inApp, onClick = run(actions::about))
			Separator()
			Item(settings, enabled = inApp, shortcut = NativeKeyShortcut(","),
				onClick = run(actions::settings))
			Separator()
			Item(quitApp, shortcut = NativeKeyShortcut("q"), onClick = quit)
		}
		Menu(view) {
			Item(refresh, enabled = canRefresh, shortcut = NativeKeyShortcut("r"),
				onClick = run(actions::refresh))
		}
		Menu(go) {
			Item(back, enabled = canGoBack, shortcut = NativeKeyShortcut("["),
				onClick = run(actions::back))
			Item(forward, enabled = canGoForward, shortcut = NativeKeyShortcut("]"),
				onClick = run(actions::forward))
			Separator()
			Item(search, enabled = inApp, shortcut = NativeKeyShortcut("f"),
				onClick = run(actions::search))
		}
		Menu(controls) {
			// no Space key equivalent: the menu would take spaces typed into text fields
			Item(playPause, enabled = hasSong, onClick = run(actions::playPause))
			Item(next, enabled = hasSong, shortcut = NativeKeyShortcut(NativeKey.RIGHT),
				onClick = run(actions::next))
			Item(previous, enabled = hasSong, shortcut = NativeKeyShortcut(NativeKey.LEFT),
				onClick = run(actions::previous))
		}
		MenuWindow(window)
	}
}
