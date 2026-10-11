package eu.depau.loak.ui.screens.lyrics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Remove
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_decrease_lyric_offset
import eu.depau.loak.generated.resources.action_increase_lyric_offset
import eu.depau.loak.generated.resources.action_lyric_offset_ms
import eu.depau.loak.generated.resources.action_save
import eu.depau.loak.generated.resources.title_sync_lyrics
import org.jetbrains.compose.resources.stringResource

/**
 * The lyrics sync tool: an overlay letting the user shift synced lines by an editable number of
 * milliseconds. Edits preview immediately; only Save bakes the offset into the stored lyrics and
 * hides the tool, Cancel discards the preview and hides it.
 */
@Composable
fun LyricsSyncTool(
	offsetMs: Long,
	onOffsetChange: (Long) -> Unit,
	onCancel: () -> Unit,
	onSave: () -> Unit,
	saving: Boolean = false,
	modifier: Modifier = Modifier
) {
	var text by remember(offsetMs) { mutableStateOf(offsetMs.toString()) }

	Surface(
		modifier = modifier,
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainer,
		tonalElevation = 6.dp
	) {
		Column(
			Modifier.padding(16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				stringResource(Res.string.title_sync_lyrics),
				style = MaterialTheme.typography.titleMedium
			)
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp)
			) {
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
			}
			Row(
				Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
			) {
				TextButton(onClick = onCancel, enabled = !saving) {
					Text(stringResource(Res.string.action_cancel))
				}
				Button(onClick = onSave, enabled = !saving) {
					Icon(Icons.Outlined.Check, null, Modifier.padding(end = 4.dp))
					Text(stringResource(Res.string.action_save))
				}
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
		textStyle = MaterialTheme.typography.titleLarge.copy(
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurface
		),
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
		singleLine = true,
		cursorBrush = SolidColor(focusColor),
		decorationBox = { inner ->
			Box(
				Modifier
					.widthIn(min = 96.dp)
					.background(
						MaterialTheme.colorScheme.surfaceVariant,
						MaterialTheme.shapes.medium
					)
					.border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
					.padding(horizontal = 12.dp, vertical = 10.dp)
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					inner()
					Text(
						" ms",
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	)
}

/** Overlay wrapper: dims the screen and centers the tool. */
@Composable
fun LyricsSyncOverlay(
	offsetMs: Long,
	onOffsetChange: (Long) -> Unit,
	onCancel: () -> Unit,
	onSave: () -> Unit,
	saving: Boolean = false
) {
	Box(
		Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .45f)),
		contentAlignment = Alignment.Center
	) {
		LyricsSyncTool(
			offsetMs = offsetMs,
			onOffsetChange = onOffsetChange,
			onCancel = onCancel,
			onSave = onSave,
			saving = saving,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
		)
	}
}
