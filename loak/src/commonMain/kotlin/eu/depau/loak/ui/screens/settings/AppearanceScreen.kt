package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_alphabetical_scroll
import eu.depau.loak.generated.resources.option_animation_style
import eu.depau.loak.generated.resources.option_artist_image_shape
import eu.depau.loak.generated.resources.option_artwork_shape
import eu.depau.loak.generated.resources.option_choose_app_icon
import eu.depau.loak.generated.resources.option_choose_theme
import eu.depau.loak.generated.resources.option_cover_art_size
import eu.depau.loak.generated.resources.option_dynamic_theming
import eu.depau.loak.generated.resources.option_enable_predictive_back_animations
import eu.depau.loak.generated.resources.option_enable_ratings
import eu.depau.loak.generated.resources.option_grid_items_per_row
import eu.depau.loak.generated.resources.option_use_marquee_text
import eu.depau.loak.generated.resources.subtitle_dynamic_theming
import eu.depau.loak.generated.resources.subtitle_enable_ratings
import eu.depau.loak.generated.resources.title_appearance
import eu.depau.loak.generated.resources.title_choose_font
import eu.depau.loak.generated.resources.title_layout
import eu.depau.loak.generated.resources.title_miscellaneous
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.AnimationStyle
import eu.depau.loak.domain.models.settings.MarqueeSpeed
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsSliderItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.screens.settings.dialogs.ArtworkShapeDialog
import eu.depau.loak.ui.screens.settings.dialogs.GridSizeDialog
import eu.depau.loak.ui.screens.settings.dialogs.GridSizePreview

@Composable
fun SettingsAppearanceScreen() {
	val preferenceManager = koinInject<PreferenceManager>()

	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current

	val isCompact = platformContext.sizeClass.widthSizeClass <= WindowWidthSizeClass.Compact
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	var showArtworkShapeDialog by rememberSaveable { mutableStateOf(false) }
	var showArtistImageShapeDialog by rememberSaveable { mutableStateOf(false) }
	var showGridSizeDialog by rememberSaveable { mutableStateOf(false) }

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
				SettingsGroup {
					val isAndroid = platformContext.platformType == PlatformType.Android
					val count = if (isAndroid) 3 else 2
					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 0,
							count = count
						),
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.Fonts)
						},
						content = { Text(stringResource(Res.string.title_choose_font)) },
						supportingContent = { Text(preferenceManager.font.displayName) }
					)
					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 1,
							count = count
						),
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.Themes)
						},
						content = { Text(stringResource(Res.string.option_choose_theme)) },
						supportingContent = { Text(stringResource(preferenceManager.theme.title)) }
					)
					if (isAndroid) {
						SegmentedListItem(
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 2,
								count = count
							),
							onClick = dropUnlessResumed {
								backStack.add(Screen.Settings.AppIcon)
							},
							content = { Text(stringResource(Res.string.option_choose_app_icon)) },
							supportingContent = { Text(preferenceManager.appIconVariant.name) }
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_layout)) }) {
					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 0,
							count = 3
						),
						onClick = { showArtworkShapeDialog = true },
						content = { Text(stringResource(Res.string.option_artwork_shape)) },
						supportingContent = { Text(preferenceManager.coverArtShape.name) },
						trailingContent = {
							val shape = preferenceManager.coverArtShape.decreasedShape
							Box(
								modifier = Modifier
									.size(48.dp)
									.clip(shape)
									.background(MaterialTheme.colorScheme.primaryContainer)
									.border(2.dp, MaterialTheme.colorScheme.primary, shape)
							)
						}
					)
					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 1,
							count = 3
						),
						onClick = { showArtistImageShapeDialog = true },
						content = { Text(stringResource(Res.string.option_artist_image_shape)) },
						supportingContent = { Text(preferenceManager.artistImageShape.name) },
						trailingContent = {
							val shape = preferenceManager.artistImageShape.decreasedShape
							Box(
								modifier = Modifier
									.size(48.dp)
									.clip(shape)
									.background(MaterialTheme.colorScheme.primaryContainer)
									.border(2.dp, MaterialTheme.colorScheme.primary, shape)
							)
						}
					)
					if (isCompact) {
						// preset sizes on phone
						SegmentedListItem(
							onClick = { showGridSizeDialog = true },
							content = { Text(stringResource(Res.string.option_grid_items_per_row)) },
							supportingContent = { Text(preferenceManager.gridSize.label) },
							trailingContent = { GridSizePreview(preferenceManager.gridSize.value) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 2,
								count = 3
							)
						)
					} else {
						// direct sizing slider on tablet
						SettingsSliderItem(
							value = preferenceManager.artGridItemSize,
							valueRange = 50f..500f,
							onValueChange = { preferenceManager.artGridItemSize = it },
							steps = 8,
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 2,
								count = 3
							),
							content = { Text(stringResource(Res.string.option_cover_art_size)) },
							trailingContent = { Text("${preferenceManager.artGridItemSize.toInt()}") }
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_miscellaneous)) }) {
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_dynamic_theming)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_dynamic_theming)) },
						checked = preferenceManager.dynamicTheming,
						onCheckedChange = { preferenceManager.dynamicTheming = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 6)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_alphabetical_scroll)) },
						checked = preferenceManager.alphabeticalScroll,
						onCheckedChange = { preferenceManager.alphabeticalScroll = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 6)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_enable_predictive_back_animations)) },
						checked = preferenceManager.enablePredictiveBackAnimations,
						onCheckedChange = { preferenceManager.enablePredictiveBackAnimations = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 6)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_enable_ratings)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_enable_ratings)) },
						checked = preferenceManager.enableRatings,
						onCheckedChange = { preferenceManager.enableRatings = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 6)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_use_marquee_text)) },
						choices = MarqueeSpeed.entries.toImmutableList(),
						selectedChoice = preferenceManager.marqueeSpeed,
						onChoiceSelected = { preferenceManager.marqueeSpeed = it },
						label = { it.name },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 6)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_animation_style)) },
						choices = AnimationStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.animationStyle,
						onChoiceSelected = { preferenceManager.animationStyle = it },
						label = { it.name },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 6)
					)
				}
			}
		}
	}

	ArtworkShapeDialog(
		title = { Text(stringResource(Res.string.option_artwork_shape)) },
		selection = preferenceManager.coverArtShape,
		onSelect = { preferenceManager.coverArtShape = it },
		presented = showArtworkShapeDialog,
		onDismissRequest = { showArtworkShapeDialog = false }
	)
	ArtworkShapeDialog(
		title = { Text(stringResource(Res.string.option_artist_image_shape)) },
		selection = preferenceManager.artistImageShape,
		onSelect = { preferenceManager.artistImageShape = it },
		presented = showArtistImageShapeDialog,
		onDismissRequest = { showArtistImageShapeDialog = false }
	)
	GridSizeDialog(
		presented = showGridSizeDialog,
		onDismissRequest = { showGridSizeDialog = false }
	)
}
