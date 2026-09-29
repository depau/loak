package eu.depau.loak.ui.screens.artist.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isLandscape
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.screens.artist.truncateText

@Composable
fun ArtistDetailScreenHeading(
	artistName: String,
	coverArtId: String?,
	subtitle: String?,
	lastfm: String?,
	innerPadding: PaddingValues,
	scrolled: Boolean
) {
	val layoutDirection = LocalLayoutDirection.current
	val progress by animateFloatAsState(if (scrolled) 0f else 1f)
	// next to the navigation rail a full-bleed fade looks cut off: the photo becomes an inset card
	val card = LocalPlatformContext.current.isLandscape()
	BoxWithConstraints(
		modifier = Modifier.fillMaxWidth()
	) {
		Box(
			modifier = if (card) Modifier
				.padding(top = innerPadding.calculateTopPadding(), start = 16.dp, end = 16.dp)
				.fillMaxWidth()
				.height(320.dp)
				.clip(ContinuousRoundedRectangle(28.dp))
				.background(MaterialTheme.colorScheme.surfaceContainer)
			else Modifier
				.fillMaxWidth()
				.height((400.dp / (maxWidth / 300.dp)) + innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surfaceContainer)
		) {
			CoverArt(
				coverArtId = coverArtId,
				modifier = Modifier.fillMaxSize(),
				shape = RectangleShape,
				square = false
			)
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						if (card) Brush.verticalGradient(
							0.35f to Color.Transparent,
							1f to Color.Black.copy(alpha = .62f)
						) else Brush.verticalGradient(
							0.0f to Color.Transparent,
							0.975f to MaterialTheme.colorScheme.background
						)
					)
			)

			Column(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(horizontal = 20.dp)
					.padding(bottom = if (card) 20.dp else 0.dp)
					.padding(start = innerPadding.calculateStartPadding(layoutDirection))
					.padding(end = innerPadding.calculateEndPadding(layoutDirection)),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				subtitle?.let { subtitle ->
					Text(
						text = buildAnnotatedString {
							append(truncateText(subtitle, 200))
							if (subtitle.length > 200 && lastfm != null) {
								append(" ")
								withLink(LinkAnnotation.Url(lastfm)) {
									append(stringResource(Res.string.action_more))
								}
							}
						},
						style = MaterialTheme.typography.bodySmall,
						color = if (card) Color.White.copy(alpha = .9f) else MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.widthIn(max = 500.dp)
					)
				}
				MarqueeText(
					text = artistName,
					style = MaterialTheme.typography.displaySmall.copy(
						fontWeight = FontWeight.Bold,
						color = if (card) Color.White else Color.Unspecified
					),
					modifier = Modifier
						.fillMaxWidth()
						.alpha(progress)
						.scale(progress)
				)
			}
		}
	}
}
