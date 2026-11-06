@file:OptIn(ExperimentalWasmJsInterop::class)

package eu.depau.loak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

@JsFun("(title) => { document.title = title; }")
private external fun setDocumentTitle(title: String)

/** Synchronizes page navigation with the browser's back and forward controls. */
@Composable
fun BrowserHistory(actions: AppActions) {
	val current by rememberUpdatedState(actions)
	val history = remember { PageHistory() }
	val pages = actions.pages
	DisposableEffect(history) {
		val listener = listenHistory { id, index ->
			history.pop(id, index)?.let { current.restorePages(it) }
		}
		onDispose { unlistenHistory(listener) }
	}
	browserPageTitle(pages.lastOrNull() as? Screen) { title ->
		history.changed(actions.inApp, pages, title)
	}
}

private class PageHistory {
	private var id = historyId()
	private var inApp: Boolean? = null
	private data class Entry(val pages: List<NavKey>, var title: String)
	private val entries = mutableListOf<Entry>()
	private var index = 0
	private var pending: Int? = null

	fun changed(signedIn: Boolean, pages: List<NavKey>, title: String) {
		if (pages.isEmpty()) return
		if (inApp != signedIn ||
			entries.getOrNull(index)?.pages?.contains(Screen.SetupFeatures) == true &&
			Screen.SetupFeatures !in pages
		) {
			inApp = signedIn
			id = historyId()
			entries.clear()
			entries.add(Entry(pages, title))
			index = 0
			pending = null
			setHistory(id, index, true)
			setDocumentTitle(title)
			return
		}
		pending?.let { target ->
			if (pages == entries[target].pages) {
				entries[target].title = title
				return
			}
		}
		if (pages == entries[index].pages) {
			entries[index].title = title
			setDocumentTitle(title)
			return
		}
		val old = entries[index].pages
		val target = when {
			pages.size > old.size && pages.take(old.size) == old &&
				index < entries.lastIndex && entries[index + 1].pages == pages -> index + 1
			pages.size < old.size && old.take(pages.size) == pages ->
				(index - 1 downTo 0).firstOrNull { entries[it].pages == pages }
			else -> null
		}
		if (target != null && pending == null) {
			pending = target
			entries[target].title = title
			goHistory(target - index)
		} else {
			while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
			entries.add(Entry(pages, title))
			index = entries.lastIndex
			pending = null
			setHistory(id, index, false)
			setDocumentTitle(title)
		}
	}

	fun pop(eventId: String, target: Int): List<NavKey>? {
		if (eventId != id || target !in entries.indices) {
			val entry = entries.getOrNull(index) ?: return null
			id = historyId()
			entries.clear()
			entries.add(entry)
			index = 0
			pending = null
			setHistory(id, index, true)
			return null
		}
		index = target
		pending = null
		setDocumentTitle(entries[index].title)
		return entries[index].pages
	}
}
