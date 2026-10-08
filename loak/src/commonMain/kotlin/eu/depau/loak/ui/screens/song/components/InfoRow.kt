package eu.depau.loak.ui.screens.song.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.ExportManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.exportOriginal
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_export_original
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Keep
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import eu.depau.loak.generated.resources.info_unknown
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.ui.components.common.SegmentedListItem

@Composable
fun SongDetailScreenInfoRow(
	shapes: ListItemShapes,
	key: String,
	value: String?
) {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current

	SegmentedListItem(
		shapes = shapes,
		contentPadding = PaddingValues(14.dp),
		overlineContent = {
			Text(
				text = key,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary
			)
		},
		content = {
			Text(
				text = value ?: stringResource(Res.string.info_unknown),
				style = MaterialTheme.typography.bodyLarge
			)
		},
		onClick = {
			if (value == null) return@SegmentedListItem
			clipboard.setText(AnnotatedString(value))
		}
	)
}

/**
 * A song's details as a segmented list, and below them a way to save its original file (not
 * on the web, which can't write files).
 */
@Composable
fun SongDetailInfo(info: List<Pair<StringResource, String?>>, song: DomainSong?) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.SegmentedGap)
	) {
		info.forEachIndexed { index, (key, value) ->
			SongDetailScreenInfoRow(
				key = stringResource(key),
				value = value,
				shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = info.count())
			)
		}
	}
	if (song == null || LocalPlatformContext.current.platformType == PlatformType.Web) return
	val exportManager = koinInject<ExportManager>()
	val sessionManager = koinInject<SessionManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	// the original raw file goes through the platform sink on demand, not the in-app download store
	FilledTonalButton(
		onClick = {
			snackBarManager.launch { exportManager.exportOriginal(song, sessionManager, snackBarManager) }
		},
		enabled = LocalAvailability.current.online,
		modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
	) {
		Icon(Icons.Outlined.Keep, null, Modifier.size(ButtonDefaults.IconSize))
		Spacer(Modifier.size(ButtonDefaults.IconSpacing))
		Text(stringResource(Res.string.action_export_original))
	}
}
