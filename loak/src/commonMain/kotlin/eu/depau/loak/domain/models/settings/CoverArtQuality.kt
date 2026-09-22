package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_quality_high
import eu.depau.loak.generated.resources.option_quality_low
import eu.depau.loak.generated.resources.option_quality_medium
import org.jetbrains.compose.resources.StringResource

enum class CoverArtQuality(
	val displayName: StringResource,
	val value: Int
) {
	Low(Res.string.option_quality_low, 512),
	Medium(Res.string.option_quality_medium, 1024),
	High(Res.string.option_quality_high, 4096)
}
