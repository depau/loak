package eu.depau.loak.ui.screens.collection.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_unknown_genre
import eu.depau.loak.generated.resources.info_unknown_year
import eu.depau.loak.generated.resources.subtitle_playlist
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.LocalSharedTransitionScope
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.ui.util.EmphasizedDecelerateEasing

@Composable
fun CollectionDetailScreenHeadingRow(
	collection: DomainSongCollection,
	tab: String,
	titleAlpha: Float
) {
	val backStack = LocalNavStack.current
	val sharedTransitionKey = "${tab}-${collection.id}-cover"
	with(LocalSharedTransitionScope.current) {
		CoverArt(
			coverArtId = collection.coverArtId,
			contentDescription = collection.name,
			modifier = Modifier
				.widthIn(0.dp, 420.dp)
				.padding(horizontal = 64.dp)
				.aspectRatio(1f)
				.then(
					// ponytail: disable sharedElement on web to avoid Lookahead layout issues
					if (LocalPlatformContext.current.platformType == PlatformType.Web) Modifier
					else Modifier.sharedElement(
						sharedContentState = this@with.rememberSharedContentState(sharedTransitionKey),
						boundsTransform = BoundsTransform { _, _ ->
							tween(
								durationMillis = 500,
								easing = EmphasizedDecelerateEasing
							)
						},
						animatedVisibilityScope = LocalNavAnimatedContentScope.current
					)
				)
				.alpha(titleAlpha),
			crossfadeMs = 0,
			onClick = collection.coverArtId?.let { coverArtId ->
				dropUnlessResumed {
					backStack.add(Screen.ImageView(
						coverArtId = coverArtId,
						title = collection.name ?: "[unknown album]",
						sharedTransitionKey = sharedTransitionKey
					))
				}
			}
		)
		Column(
			modifier = Modifier
				.padding(horizontal = 31.dp)
				.padding(top = 10.dp, bottom = 8.dp)
				.alpha(titleAlpha),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				collection.name ?: "[unknown album]",
				style = MaterialTheme.typography.headlineSmall,
				textAlign = TextAlign.Center,
				modifier = Modifier
			)
			val subtitle = when (collection) {
				is DomainAlbum -> collection.artistName
				is DomainPlaylist -> collection.comment
			}
			subtitle?.let { subtitle ->
				Text(
					subtitle,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.clickable(
						collection is DomainAlbum,
						onClick = dropUnlessResumed {
							(collection as? DomainAlbum)?.artistId?.let { id ->
								backStack.add(Screen.ArtistDetail(id))
							}
						}),
					style = MaterialTheme.typography.bodyMedium,
					fontFamily = defaultFont(grade = 100, round = 100f)
				)
			}
			Text(
				if (collection is DomainAlbum)
					"${collection.genre ?: stringResource(Res.string.info_unknown_genre)} • ${
						collection.year ?: stringResource(
							Res.string.info_unknown_year
						)
					}"
				else stringResource(Res.string.subtitle_playlist),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.bodySmall,
				fontFamily = defaultFont(grade = 100, round = 100f)
			)
		}
	}
}
