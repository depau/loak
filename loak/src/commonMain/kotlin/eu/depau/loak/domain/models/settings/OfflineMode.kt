package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_offline_mode_auto
import eu.depau.loak.generated.resources.option_offline_mode_forced
import eu.depau.loak.generated.resources.option_offline_mode_no_wifi
import org.jetbrains.compose.resources.StringResource

enum class OfflineMode(val displayName: StringResource) {
	Auto(Res.string.option_offline_mode_auto),
	Forced(Res.string.option_offline_mode_forced),
	NoWiFi(Res.string.option_offline_mode_no_wifi),
}
