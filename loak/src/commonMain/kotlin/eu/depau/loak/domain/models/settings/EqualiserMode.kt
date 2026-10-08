package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_equaliser_mode_builtin
import eu.depau.loak.generated.resources.option_equaliser_mode_disabled
import eu.depau.loak.generated.resources.option_equaliser_mode_external
import org.jetbrains.compose.resources.StringResource

// Which equaliser processes Lo'ak's audio session. Builtin/External need to be mutually exclusive,
// otherwise they will fight for effect control and cause audio issues
enum class EqualiserMode(val displayName: StringResource) {
	Disabled(Res.string.option_equaliser_mode_disabled),
	BuiltIn(Res.string.option_equaliser_mode_builtin),
	External(Res.string.option_equaliser_mode_external)
}
