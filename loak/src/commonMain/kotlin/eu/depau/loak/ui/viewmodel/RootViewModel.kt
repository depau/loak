package eu.depau.loak.ui.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Events from the app's chrome to the screens, such as a tap on the tab already open.
 * A Koin single: the navigation rail sits outside the screens' ViewModel stores, so a
 * ViewModel here gave the rail and the screens separate instances.
 */
class RootViewModel {
	val events: SharedFlow<Event>
		field = MutableSharedFlow<Event>(extraBufferCapacity = 1)

	fun requestScrollToTop() {
		events.tryEmit(Event.ScrollToTop)
	}

	sealed class Event {
		object ScrollToTop : Event()
	}
}
