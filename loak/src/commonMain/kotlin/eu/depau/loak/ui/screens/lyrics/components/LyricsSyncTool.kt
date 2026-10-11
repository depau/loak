package eu.depau.loak.ui.screens.lyrics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Remove
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_decrease_lyric_offset
import eu.depau.loak.generated.resources.action_increase_lyric_offset
import eu.depau.loak.generated.resources.action_save
import org.jetbrains.compose.resources.stringResource

/**
 * The lyrics sync tool: an inline bar letting the user shift synced lines by an editable number of
 * milliseconds. Edits preview immediately; Save bakes the offset into the stored lyrics and hides
 * the tool, Cancel (the X) discards the preview and hides it. The lyrics stay fully readable.
 */
@Composable
fun LyricsSyncTool(
	offsetMs: Long,
	onOffsetChange: (Long) -> Unit,
	onCancel: () -> Unit,
	onSave: () -> Unit,
	saving: Boolean = false
) {
	var text by remember(offsetMs) { mutableStateOf(offsetMs.toString()) }

	Surface(
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainer,
		tonalElevation = 6.dp
	) {
		Row(
			Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(2.dp)
		) {
			// Cancel (X) on the opposite side from Save
			IconButton(onClick = onCancel, enabled = !saving) {
				Icon(Icons.Outlined.Close, stringResource(Res.string.action_cancel))
			}
			IconButton(onClick = { onOffsetChange(offsetMs - 50) }, enabled = !saving) {
				Icon(
					Icons.Outlined.Remove,
					stringResource(Res.string.action_decrease_lyric_offset)
				)
			}
			OffsetInput(
				value = text,
				onValueChange = { newText, newValue ->
					text = newText
					onOffsetChange(newValue)
				},
				enabled = !saving
			)
			IconButton(onClick = { onOffsetChange(offsetMs + 50) }, enabled = !saving) {
				Icon(
					Icons.Outlined.Add,
					stringResource(Res.string.action_increase_lyric_offset)
				)
			}
			IconButton(onClick = onSave, enabled = !saving) {
				Icon(Icons.Outlined.Check, stringResource(Res.string.action_save))
			}
		}
	}
}

@Composable
private fun OffsetInput(
	value: String,
	onValueChange: (String, Long) -> Unit,
	enabled: Boolean = true
) {
	val focusColor = MaterialTheme.colorScheme.primary
	BasicTextField(
		value = value,
		onValueChange = { input ->
			val negative = input.trim().startsWith("-")
			val digits = input.filter { it.isDigit() }.take(7)
			val newText = (if (negative) "-" else "") + digits
			val newValue = newText.toLongOrNull() ?: 0L
			onValueChange(newText, newValue)
		},
		enabled = enabled,
		textStyle = MaterialTheme.typography.titleMedium.copy(
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurface
		),
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
		singleLine = true,
		cursorBrush = SolidColor(focusColor),
		decorationBox = { inner ->
			Box(
				Modifier
					.background(
						MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
						MaterialTheme.shapes.medium
					)
					.padding(horizontal = 10.dp, vertical = 8.dp)
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					inner()
					Text(
						" ms",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	)
}
