package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_audio_cache_limit_size
import eu.depau.loak.generated.resources.option_audio_cache_limit_songs
import org.jetbrains.compose.resources.StringResource

/** What bounds the streamed-audio cache. */
enum class AudioCacheLimit(val displayName: StringResource) {
	Size(Res.string.option_audio_cache_limit_size),
	Songs(Res.string.option_audio_cache_limit_songs),
}
