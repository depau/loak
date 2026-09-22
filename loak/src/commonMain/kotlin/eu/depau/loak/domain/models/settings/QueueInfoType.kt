package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_queue_info_type_full
import eu.depau.loak.generated.resources.option_queue_info_type_remining
import org.jetbrains.compose.resources.StringResource

enum class QueueInfoType(val displayName: StringResource) {
	Full(Res.string.option_queue_info_type_full),
	Remaining(Res.string.option_queue_info_type_remining);
}
