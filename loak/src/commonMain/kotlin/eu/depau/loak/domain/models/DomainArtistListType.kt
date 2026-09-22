package eu.depau.loak.domain.models

import androidx.compose.runtime.Immutable
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_sort_alphabetical_by_name
import eu.depau.loak.generated.resources.option_sort_random
import org.jetbrains.compose.resources.StringResource

@Immutable
enum class DomainArtistListType(val displayName: StringResource) {
	AlphabeticalByName(Res.string.option_sort_alphabetical_by_name),
	Random(Res.string.option_sort_random)
}
