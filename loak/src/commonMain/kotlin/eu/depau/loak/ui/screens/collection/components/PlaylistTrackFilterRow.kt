package eu.depau.loak.ui.screens.collection.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_sort_ascending
import eu.depau.loak.generated.resources.option_sort_descending
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.hint_filter_tracks
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.ArrowDown
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.theme.defaultFont
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
@Composable
fun PlaylistTrackFilterRow(
	query: TextFieldState,
	filteredCount: Int,
	totalCount: Int,
	isReversed: Boolean,
	onToggleReversed: () -> Unit,
	modifier: Modifier = Modifier
) {
	val expanded = LocalPlatformContext.current.isExpanded()
	val rotation by animateFloatAsState(if (isReversed) 0f else 180f)

	val countText = if (query.text.isNotEmpty()) {
		buildAnnotatedString {
			withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
				append(filteredCount.toString())
			}
			append(" of ")
			append(pluralStringResource(Res.plurals.count_songs, totalCount, totalCount))
		}
	} else {
		buildAnnotatedString {
			append(pluralStringResource(Res.plurals.count_songs, totalCount, totalCount))
		}
	}

	val sortButton = @Composable {
		val contentDescription = stringResource(
			if (isReversed) Res.string.option_sort_descending else Res.string.option_sort_ascending
		)
		Box(
			modifier = Modifier
				.size(48.dp)
				.semantics(mergeDescendants = true) {
					this.contentDescription = contentDescription
					role = Role.Button
				}
				.clickable(onClick = onToggleReversed),
			contentAlignment = Alignment.Center
		) {
			Box(
				modifier = Modifier
					.size(width = 38.dp, height = 30.dp)
					.border(
						width = 1.dp,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RoundedCornerShape(15.dp)
					)
					.background(
						color = MaterialTheme.colorScheme.surfaceContainerLow,
						shape = RoundedCornerShape(15.dp)
					),
				contentAlignment = Alignment.Center
			) {
				Icon(
					imageVector = Icons.Outlined.ArrowDown,
					contentDescription = null,
					modifier = Modifier.size(16.dp).rotate(rotation),
					tint = MaterialTheme.colorScheme.onSurface
				)
			}
		}
	}

	val searchField = @Composable { fieldModifier: Modifier ->
		BasicTextField(
			state = query,
			modifier = fieldModifier
				.height(52.dp)
				.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
				.padding(horizontal = 16.dp)
				.onKeyEvent { event ->
					// Consume spacebar event so it does not bubble to global media play/pause
					if (event.key == Key.Spacebar) true else false
				},
			lineLimits = TextFieldLineLimits.SingleLine,
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
			textStyle = TextStyle(
				color = MaterialTheme.colorScheme.onSurface,
				fontFamily = defaultFont()
			),
			cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
			decorator = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					Icon(
						imageVector = Icons.Outlined.Search,
						contentDescription = null,
						modifier = Modifier.size(20.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
						if (query.text.isEmpty()) {
							Text(
								text = stringResource(Res.string.hint_filter_tracks),
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								style = MaterialTheme.typography.bodyMedium
							)
						}
						innerTextField()
					}
					if (query.text.isNotEmpty()) {
						IconButton(
							onClick = { query.clearText() },
							modifier = Modifier.size(28.dp)
						) {
							Icon(
								imageVector = Icons.Outlined.Close,
								contentDescription = null,
								modifier = Modifier.size(18.dp),
								tint = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}
		)
	}

	if (expanded) {
		Row(
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp)
		) {
			searchField(Modifier.weight(1f, fill = false).widthIn(max = 560.dp))
			Text(
				text = countText,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			sortButton()
		}
	} else {
		Column(
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp)
		) {
			searchField(Modifier.fillMaxWidth())
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					text = countText,
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				sortButton()
			}
		}
	}
}
