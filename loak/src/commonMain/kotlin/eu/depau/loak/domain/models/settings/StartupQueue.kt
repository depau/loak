package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_startup_queue_local
import eu.depau.loak.generated.resources.option_startup_queue_nothing
import eu.depau.loak.generated.resources.option_startup_queue_playlist
import eu.depau.loak.generated.resources.option_startup_queue_server
import org.jetbrains.compose.resources.StringResource

/** What the queue holds when the app starts. */
enum class StartupQueue(val displayName: StringResource) {
	/** The queue the server keeps, saved by whichever device played last; this device's when offline. */
	Server(Res.string.option_startup_queue_server),
	Local(Res.string.option_startup_queue_local),
	/** [eu.depau.loak.domain.manager.PreferenceManager.startupPlaylistId], fetched again each start. */
	Playlist(Res.string.option_startup_queue_playlist),
	Nothing(Res.string.option_startup_queue_nothing)
}
