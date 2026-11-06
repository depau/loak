@file:OptIn(ExperimentalWasmJsInterop::class)

package eu.depau.loak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavKey

@JsFun("() => Math.random().toString(36).slice(2)")
private external fun historyId(): String

@JsFun("(id, index, replace) => { const state = { loakHistory: id, index }; if (replace) history.replaceState(state, ''); else history.pushState(state, ''); }")
private external fun setHistory(id: String, index: Int, replace: Boolean)

@JsFun("(delta) => history.go(delta)")
private external fun goHistory(delta: Int)

@JsFun("(callback) => { const listener = e => callback(e.state?.loakHistory ?? '', e.state?.index ?? -1); window.addEventListener('popstate', listener); return listener; }")
private external fun listenHistory(callback: (String, Int) -> Unit): JsAny

@JsFun("(listener) => window.removeEventListener('popstate', listener)")
private external fun unlistenHistory(listener: JsAny)

/** Synchronizes page navigation with the browser's back and forward controls. */
@Composable
fun BrowserHistory(actions: AppActions) {
	val current by rememberUpdatedState(actions)
	val history = remember { PageHistory() }
	DisposableEffect(history) {
		val listener = listenHistory { id, index ->
			history.pop(id, index)?.let { current.restorePages(it) }
		}
		onDispose { unlistenHistory(listener) }
	}
	LaunchedEffect(history) {
		snapshotFlow { current.inApp to current.pages }.collect { (inApp, pages) ->
			history.changed(inApp, pages)
		}
	}
}

private class PageHistory {
	private var id = historyId()
	private var inApp: Boolean? = null
	private val entries = mutableListOf<List<NavKey>>()
	private var index = 0
	private var pending: Int? = null

	fun changed(signedIn: Boolean, pages: List<NavKey>) {
		if (pages.isEmpty()) return
		if (inApp != signedIn ||
			entries.getOrNull(index)?.contains(Screen.SetupFeatures) == true &&
			Screen.SetupFeatures !in pages
		) {
			inApp = signedIn
			id = historyId()
			entries.clear()
			entries.add(pages)
			index = 0
			pending = null
			setHistory(id, index, true)
			return
		}
		if (pages == entries[index]) return
		val old = entries[index]
		val target = when {
			pages.size > old.size && pages.take(old.size) == old &&
				index < entries.lastIndex && entries[index + 1] == pages -> index + 1
			pages.size < old.size && old.take(pages.size) == pages ->
				(index - 1 downTo 0).firstOrNull { entries[it] == pages }
			else -> null
		}
		if (target != null && pending == null) {
			pending = target
			goHistory(target - index)
		} else {
			while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
			entries.add(pages)
			index = entries.lastIndex
			pending = null
			setHistory(id, index, false)
		}
	}

	fun pop(eventId: String, target: Int): List<NavKey>? {
		if (eventId != id || target !in entries.indices) {
			val pages = entries.getOrNull(index) ?: return null
			id = historyId()
			entries.clear()
			entries.add(pages)
			index = 0
			pending = null
			setHistory(id, index, true)
			return null
		}
		index = target
		pending = null
		return entries[index]
	}
}
