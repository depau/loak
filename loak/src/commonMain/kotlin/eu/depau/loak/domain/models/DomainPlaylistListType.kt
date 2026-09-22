package eu.depau.loak.domain.models

import androidx.compose.runtime.Immutable
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_sort_playlist_by_name
import eu.depau.loak.generated.resources.option_sort_playlist_date_added
import eu.depau.loak.generated.resources.option_sort_playlist_duration
import eu.depau.loak.generated.resources.option_sort_random
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainPlaylistListType(val displayName: StringResource) {
	Name(Res.string.option_sort_playlist_by_name),
	DateAdded(Res.string.option_sort_playlist_date_added),
	Duration(Res.string.option_sort_playlist_duration),
	Random(Res.string.option_sort_random)
}
