package eu.depau.loak.ui.screens.home.components

import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.onSecondaryClick
import eu.depau.loak.ui.util.shimmerLoading
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eu.depau.loak.ui.components.sheets.SongActionsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.ServerListener
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_all_genres
import eu.depau.loak.generated.resources.action_instant_mix
import eu.depau.loak.generated.resources.action_play_sonic_journey
import eu.depau.loak.generated.resources.info_listener_minutes
import eu.depau.loak.generated.resources.info_listener_now
import eu.depau.loak.generated.resources.info_sonic_journey
import eu.depau.loak.generated.resources.label_mix
import eu.depau.loak.generated.resources.title_artist_mix
import eu.depau.loak.generated.resources.title_sonic_journey
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource

/**
 * A shelf title with an optional action on the right, like the library's headers. With
 * [onClick] the title is a link: tappable, with a chevron after it like Speed dial's.
 */
@Composable
fun ShelfHeader(
	title: String,
	modifier: Modifier = Modifier,
	leading: (@Composable () -> Unit)? = null,
	onClick: (() -> Unit)? = null,
	action: (@Composable () -> Unit)? = null
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.padding(top = 12.dp, start = 16.dp, end = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(
			modifier = Modifier.weight(1f),
			verticalAlignment = Alignment.CenterVertically
		) {
			Row(
				modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
				else Modifier,
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				leading?.invoke()
				Text(
					title,
					style = MaterialTheme.typography.titleMediumEmphasized,
					fontWeight = FontWeight(600),
					modifier = Modifier.weight(1f, fill = false).semantics { heading() }
				)
				if (onClick != null) Icon(Icons.Outlined.ChevronForward, null, Modifier.size(20.dp))
			}
		}
		action?.invoke()
	}
}

/** [ShelfHeader]'s skeleton, for a shelf whose title isn't known yet. */
@Composable
fun ShelfHeaderPlaceholder() {
	Row(
		modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp, start = 16.dp, end = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Box(Modifier.size(40.dp).clip(CircleShape).shimmerLoading())
		Box(Modifier.width(160.dp).height(18.dp).clip(CircleShape).shimmerLoading())
	}
}

@Composable
fun SmallOutlinedButton(text: String, onClick: () -> Unit) {
	OutlinedButton(
		onClick = onClick,
		contentPadding = PaddingValues(horizontal = 14.dp),
		modifier = Modifier.height(32.dp)
	) {
		Text(text, style = MaterialTheme.typography.labelLarge)
	}
}

/** Genre chips: one filters the feed, tapping it again clears it; the last opens Genres. */
@Composable
fun GenreChips(genres: List<String>, selected: String?, onSelect: (String?) -> Unit) {
	val backStack = LocalNavStack.current
	LazyRow(
		modifier = Modifier.verticalWheelToParent(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		contentPadding = PaddingValues(horizontal = 16.dp)
	) {
		items(genres, key = { it }) { genre ->
			FilterChip(
				selected = genre == selected,
				onClick = { onSelect(if (genre == selected) null else genre) },
				label = { Text(genre) },
				// the X tells that tapping it again clears the filter
				trailingIcon = if (genre == selected) {
					{ Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)) }
				} else null
			)
		}
		item(key = "all genres") {
			FilterChip(
				selected = false,
				onClick = dropUnlessResumed { backStack.add(Screen.GenreList(true)) },
				label = { Text(stringResource(Res.string.action_all_genres)) },
				trailingIcon = { Icon(Icons.Outlined.ChevronForward, null, Modifier.size(18.dp)) }
			)
		}
	}
}

/** An Instant mix seeded by an artist: the artist's picture with "Mix" under it. */
@Composable
fun MixCard(artist: DomainArtist, width: Dp, onClick: () -> Unit) {
	Column(
		Modifier
			.width(width)
			// the server picks the songs
			.unavailable(!LocalAvailability.current.online)
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick)
	) {
		Box(
			Modifier
				.size(width)
				.clip(RoundedCornerShape(12.dp))
				.background(MaterialTheme.colorScheme.surfaceContainerHigh)
		) {
			CoverArt(
				coverArtId = artist.coverArtId,
				modifier = Modifier.align(Alignment.TopCenter).padding(top = width * .12f).size(width * .58f),
				shape = CircleShape
			)
			Text(
				stringResource(Res.string.label_mix),
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surface.copy(alpha = .88f))
					.padding(horizontal = 10.dp, vertical = 6.dp),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			stringResource(Res.string.title_artist_mix, artist.name),
			modifier = Modifier.padding(top = 6.dp),
			style = MaterialTheme.typography.bodyMedium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			stringResource(Res.string.action_instant_mix),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/** A sonic path from one song to another, played in one tap. */
@Composable
fun SonicJourneyCard(from: DomainSong, to: DomainSong, onPlay: () -> Unit) {
	Surface(
		onClick = onPlay,
		shape = RoundedCornerShape(20.dp),
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).widthIn(max = 560.dp).fillMaxWidth()
	) {
		Row(
			Modifier.height(104.dp).padding(start = 16.dp, end = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			CoverArt(coverArtId = from.coverArtId, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp))
			Box(Modifier.width(12.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
			CoverArt(coverArtId = to.coverArtId, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp))
			Column(Modifier.weight(1f)) {
				Text(
					stringResource(Res.string.title_sonic_journey),
					style = MaterialTheme.typography.titleMedium
				)
				Text(
					stringResource(Res.string.info_sonic_journey, from.title, to.title),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
			FilledIconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
				Icon(Icons.Filled.Play, stringResource(Res.string.action_play_sonic_journey))
			}
		}
	}
}

/** A song someone else is playing, with who and when. */
@Composable
fun ListenerRow(listener: ServerListener, onClick: () -> Unit) {
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	val onLongClick = { sheetOpen = true }
	SongActionsSheet(song = listener.song, open = sheetOpen, onDismissRequest = { sheetOpen = false })
	ListItem(
		modifier = Modifier.onSecondaryClick(onLongClick).combinedClickable(onClick = onClick, onLongClick = onLongClick),
		colors = ListItemDefaults.colors(containerColor = Color.Transparent),
		leadingContent = {
			CoverArt(coverArtId = listener.song.coverArtId, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp))
		},
		headlineContent = {
			Text(
				listOfNotNull(listener.song.title, listener.song.artistName).joinToString(" · "),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		},
		supportingContent = {
			Text(
				if (listener.minutesAgo <= 0) stringResource(Res.string.info_listener_now, listener.username)
				else stringResource(Res.string.info_listener_minutes, listener.username, listener.minutesAgo)
			)
		},
		trailingContent = {
			Box(
				Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
				contentAlignment = Alignment.Center
			) {
				Text(
					listener.username.take(1).uppercase(),
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSecondaryContainer
				)
			}
		}
	)
}
