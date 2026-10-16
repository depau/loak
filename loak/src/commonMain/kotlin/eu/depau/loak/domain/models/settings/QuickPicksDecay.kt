package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_decay_long
import eu.depau.loak.generated.resources.info_decay_medium
import eu.depau.loak.generated.resources.info_decay_short
import eu.depau.loak.generated.resources.option_decay_long
import eu.depau.loak.generated.resources.option_decay_medium
import eu.depau.loak.generated.resources.option_decay_short
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How fast Quick picks forgets a song, as the half-life of a listen's weight. The server only
 * knows how many times a song was played in total, so this is what turns "played a lot, long
 * ago" into "worth suggesting again" while a song on repeat sinks instead of vanishing.
 *
 * All of them fit inside the 30-day heat window, which is what a source can still be asked
 * about; the half-life is only how sharply that window's plays are weighted.
 */
enum class QuickPicksDecay(
	val displayName: StringResource,
	val description: StringResource,
	val days: Double
) {
	/** Three days: only this week's heavy rotation leads the list. */
	Short(Res.string.option_decay_short, Res.string.info_decay_short, 3.0),

	/** Two weeks: a song played daily for a fortnight stays near the top of the repeats. */
	Medium(Res.string.option_decay_medium, Res.string.info_decay_medium, 14.0),

	/** A month: what you played at any point this month keeps its lead. */
	Long(Res.string.option_decay_long, Res.string.info_decay_long, 30.0);

	val halfLife: Duration get() = days.days
}
