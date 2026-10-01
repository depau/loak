package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.count_days_ago
import eu.depau.loak.generated.resources.count_hours
import eu.depau.loak.generated.resources.count_hours_ago
import eu.depau.loak.generated.resources.count_minutes
import eu.depau.loak.generated.resources.count_minutes_ago
import eu.depau.loak.generated.resources.info_sync_just_now
import eu.depau.loak.generated.resources.option_sort_alphabetical_by_artist
import eu.depau.loak.generated.resources.option_sort_alphabetical_by_name
import eu.depau.loak.generated.resources.option_sort_by_genre
import eu.depau.loak.generated.resources.option_sort_by_year
import eu.depau.loak.generated.resources.option_sort_frequent
import eu.depau.loak.generated.resources.option_sort_newest
import eu.depau.loak.generated.resources.option_sort_random
import eu.depau.loak.generated.resources.option_sort_rating
import eu.depau.loak.generated.resources.option_sort_recent
import eu.depau.loak.generated.resources.option_sort_year
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.domain.models.lyrics.LyricsProvider
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Clock
import kotlin.time.Instant

/** How long ago [this] was: "just now", "3 minutes ago", "2 hours ago", "5 days ago". */
@Composable
fun Instant.timeAgo(): String {
	val duration = Clock.System.now() - this
	val minutes = duration.inWholeMinutes.toInt()
	val hours = duration.inWholeHours.toInt()
	val days = duration.inWholeDays.toInt()

	return when {
		minutes < 1 -> stringResource(Res.string.info_sync_just_now)
		hours < 1 -> pluralStringResource(Res.plurals.count_minutes_ago, minutes, minutes)
		days < 1 -> pluralStringResource(Res.plurals.count_hours_ago, hours, hours)
		else -> pluralStringResource(Res.plurals.count_days_ago, days, days)
	}
}

@Composable
fun Duration.label(): String {
	val hours = inWholeHours.toInt()
	val minutes = (this - hours.hours).inWholeMinutes.toInt()

	return when {
		hours > 0 && minutes > 0 ->
			"${pluralStringResource(Res.plurals.count_hours, hours, hours)} ${
				pluralStringResource(
					Res.plurals.count_minutes,
					minutes,
					minutes
				)
			}"

		hours > 0 ->
			pluralStringResource(Res.plurals.count_hours, hours, hours)

		else ->
			pluralStringResource(Res.plurals.count_minutes, max(1, minutes), max(1, minutes))
	}
}

@Composable
fun DomainAlbumListType.label() = when (this) {
	DomainAlbumListType.Random -> stringResource(Res.string.option_sort_random)
	DomainAlbumListType.Newest -> stringResource(Res.string.option_sort_newest)
	DomainAlbumListType.Frequent -> stringResource(Res.string.option_sort_frequent)
	DomainAlbumListType.Recent -> stringResource(Res.string.option_sort_recent)
	DomainAlbumListType.AlphabeticalByName -> stringResource(Res.string.option_sort_alphabetical_by_name)
	DomainAlbumListType.AlphabeticalByArtist -> stringResource(Res.string.option_sort_alphabetical_by_artist)
	DomainAlbumListType.Highest -> stringResource(Res.string.option_sort_rating)
	DomainAlbumListType.Year -> stringResource(Res.string.option_sort_year)
	is DomainAlbumListType.ByGenre -> stringResource(Res.string.option_sort_by_genre)
	is DomainAlbumListType.ByYear -> stringResource(Res.string.option_sort_by_year)
}

@Composable
fun DomainSongListType.label() = when (this) {
	is DomainSongListType.ByArtist -> stringResource(Res.string.option_sort_by_genre)
	is DomainSongListType.ByGenre -> stringResource(Res.string.option_sort_by_genre)
	DomainSongListType.FrequentlyPlayed -> stringResource(Res.string.option_sort_frequent)
	DomainSongListType.Newest -> stringResource(Res.string.option_sort_newest)
	DomainSongListType.Random -> stringResource(Res.string.option_sort_random)
	DomainSongListType.Rating -> stringResource(Res.string.option_sort_rating)
	DomainSongListType.Year -> stringResource(Res.string.option_sort_by_year)
}

fun PaletteStyle.label(): String = when (this) {
	PaletteStyle.TonalSpot -> "Tonal Spot"
	PaletteStyle.Neutral -> "Neutral"
	PaletteStyle.Vibrant -> "Vibrant"
	PaletteStyle.Expressive -> "Expressive"
	PaletteStyle.Rainbow -> "Rainbow"
	PaletteStyle.FruitSalad -> "Fruit Salad"
	PaletteStyle.Monochrome -> "Monochrome"
	PaletteStyle.Fidelity -> "Fidelity"
	PaletteStyle.Content -> "Content"
}

fun ColorSpec.SpecVersion.label() = when (this) {
	ColorSpec.SpecVersion.SPEC_2021 -> "Material 3 (2021)"
	ColorSpec.SpecVersion.SPEC_2025 -> "Expressive (2025)"
}

fun LyricsProvider.Id.label() = when (this) {
	LyricsProvider.Id.SUBSONIC -> "Subsonic"
	LyricsProvider.Id.LYRICS_PLUS -> "LyricsPlus"
	LyricsProvider.Id.LRCLIB -> "LRCLIB"
}
