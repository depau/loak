package eu.depau.loak.domain.models.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class EqualiserConfig(
	val bandLevels: Map<Int, Float> = emptyMap(),
	val bandCount: Int = 0,
	/** Centre frequency of each band, in Hz; empty where the platform doesn't report it. */
	val bandFrequencies: List<Int> = emptyList(),
	val bandLowerRange: Float = 0f,
	val bandUpperRange: Float = 0f,
	val mode: EqualiserMode = EqualiserMode.Disabled
)
