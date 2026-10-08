package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import dev.zt64.compose.pipette.HsvColor
import dev.zt64.compose.pipette.RingColorPicker
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.Theme
import eu.depau.loak.domain.models.settings.ThemeMode
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_accent_colour
import eu.depau.loak.generated.resources.option_dynamic_theming
import eu.depau.loak.generated.resources.option_enable_ratings
import eu.depau.loak.generated.resources.option_match_accent
import eu.depau.loak.generated.resources.option_match_wallpaper
import eu.depau.loak.generated.resources.option_palette_style
import eu.depau.loak.generated.resources.option_pick_color
import eu.depau.loak.generated.resources.subtitle_dynamic_theming
import eu.depau.loak.generated.resources.subtitle_enable_ratings
import eu.depau.loak.generated.resources.subtitle_match_system
import eu.depau.loak.generated.resources.title_appearance
import eu.depau.loak.generated.resources.title_colors
import eu.depau.loak.generated.resources.title_library
import eu.depau.loak.generated.resources.title_theme
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsRadioItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.util.escapeToDismiss
import eu.depau.loak.ui.util.label
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsAppearanceScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_appearance)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup(title = { Text(stringResource(Res.string.title_theme)) }) {
					// flipped often enough to deserve an inline control instead of a dialog
					SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
						ThemeMode.entries.forEachIndexed { index, mode ->
							SegmentedButton(
								shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
								onClick = { preferenceManager.themeMode = mode },
								selected = preferenceManager.themeMode == mode,
								label = { Text(stringResource(mode.title)) }
							)
						}
					}
				}

				ColorsGroup()

				SettingsGroup(title = { Text(stringResource(Res.string.title_library)) }) {
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_dynamic_theming)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_dynamic_theming)) },
						checked = preferenceManager.dynamicTheming,
						onCheckedChange = { preferenceManager.dynamicTheming = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_enable_ratings)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_enable_ratings)) },
						checked = preferenceManager.enableRatings,
						onCheckedChange = { preferenceManager.enableRatings = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}
}

/**
 * Follow the system's colors, where it has any (Android's wallpaper, the desktop accent), or
 * pick an accent; elsewhere only the picker.
 */
@Composable
private fun ColorsGroup() {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val hasSystemColors = platformContext.systemColorScheme(isDark = false) != null
	val seeded = preferenceManager.theme == Theme.Seeded
	// the Android wallpaper scheme comes prebuilt; a desktop accent is seeded like a picked color
	val styled = seeded || platformContext.platformType == PlatformType.Desktop
	val rows = (if (hasSystemColors) 2 else 1) + (if (styled) 1 else 0)

	SettingsGroup(title = { Text(stringResource(Res.string.title_colors)) }) {
		if (hasSystemColors) {
			val desktop = platformContext.platformType == PlatformType.Desktop
			SettingsRadioItem(
				selected = !seeded,
				onClick = { preferenceManager.theme = Theme.Dynamic },
				content = {
					Text(stringResource(
						if (desktop) Res.string.option_match_accent else Res.string.option_match_wallpaper
					))
				},
				supportingContent = { Text(stringResource(Res.string.subtitle_match_system)) },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = rows)
			)
		}
		AccentPicker(
			title = if (hasSystemColors) Res.string.option_pick_color else Res.string.option_accent_colour,
			selected = seeded,
			radio = hasSystemColors,
			shapes = SegmentedListItemDefaults.segmentedShapes(
				index = if (hasSystemColors) 1 else 0,
				count = rows
			)
		)
		if (styled) {
			SettingsChoiceItem(
				choices = PaletteStyle.entries.toImmutableList(),
				selectedChoice = preferenceManager.paletteStyle,
				onChoiceSelected = { preferenceManager.paletteStyle = it },
				content = { Text(stringResource(Res.string.option_palette_style)) },
				label = { it.label() },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = rows - 1, count = rows)
			)
		}
	}
}

/** Picks the accent hue; picking one switches to the picked color. */
@Composable
private fun AccentPicker(
	title: StringResource,
	selected: Boolean,
	radio: Boolean,
	shapes: ListItemShapes
) {
	val preferenceManager = koinInject<PreferenceManager>()
	var expanded by remember { mutableStateOf(false) }
	val swatch = @Composable {
		Box {
			Box(
				Modifier
					.clip(CircleShape)
					.background(
						HsvColor(
							hue = preferenceManager.paletteAccentH,
							saturation = 1f,
							value = 1f
						).toColor()
					)
					.size(40.dp)
					.clickable { expanded = true }
			)
			// TODO: make a proper color picker sheet
			DropdownMenu(
				expanded = expanded,
				onDismissRequest = { expanded = false },
				modifier = Modifier.escapeToDismiss { expanded = false }
			) {
				RingColorPicker(
					color = {
						HsvColor(
							hue = preferenceManager.paletteAccentH,
							saturation = 1f,
							value = 1f
						)
					},
					onColorChange = { color ->
						preferenceManager.paletteAccentH = color.hue
						preferenceManager.theme = Theme.Seeded
					}
				)
			}
		}
	}

	if (radio) {
		SegmentedListItem(
			onClick = {
				preferenceManager.theme = Theme.Seeded
				expanded = true
			},
			selected = selected,
			shapes = shapes,
			leadingContent = {
				RadioButton(selected = selected, onClick = null)
			},
			content = { Text(stringResource(title)) },
			trailingContent = swatch
		)
	} else {
		SegmentedListItem(
			onClick = { expanded = true },
			shapes = shapes,
			content = { Text(stringResource(title)) },
			trailingContent = swatch
		)
	}
}
