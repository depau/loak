package eu.depau.loak.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.Feature
import eu.depau.loak.domain.manager.FeatureCatalog
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.ServerInfo
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_new
import eu.depau.loak.generated.resources.info_audiomuse_set_up
import eu.depau.loak.generated.resources.info_discover_set_up
import eu.depau.loak.generated.resources.info_discover_set_up_listenbrainz
import eu.depau.loak.generated.resources.info_discover_uses
import eu.depau.loak.generated.resources.info_feature_audiomuse
import eu.depau.loak.generated.resources.info_feature_discover
import eu.depau.loak.generated.resources.info_feature_history
import eu.depau.loak.generated.resources.info_feature_new_album_alerts
import eu.depau.loak.generated.resources.info_feature_smart_playlists
import eu.depau.loak.generated.resources.info_history_set_up
import eu.depau.loak.generated.resources.info_history_uses
import eu.depau.loak.generated.resources.info_audiomuse_uses
import eu.depau.loak.generated.resources.info_notifications_disabled
import eu.depau.loak.generated.resources.info_smart_playlists_unavailable
import eu.depau.loak.generated.resources.option_smart_playlists
import eu.depau.loak.generated.resources.title_feature_audiomuse
import eu.depau.loak.generated.resources.title_feature_discover
import eu.depau.loak.generated.resources.title_feature_new_album_alerts
import eu.depau.loak.generated.resources.title_listening_history
import eu.depau.loak.icons.Icons
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.icons.brand.Deezer
import eu.depau.loak.icons.brand.Lastfm
import eu.depau.loak.icons.brand.Lidarr
import eu.depau.loak.icons.filled.CheckCircle
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Explore
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.Notifications
import eu.depau.loak.icons.outlined.PlaylistAddCheck
import eu.depau.loak.icons.outlined.Soundwave
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.historySourceLabel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * [features] as cards (by default every one this device can have): a switch for the ones that
 * only turn on, the way to their setup page for the rest. Set-up cards turn tonal and say how;
 * the ones in [new] wear a badge.
 */
@Composable
fun FeatureCards(
	modifier: Modifier = Modifier,
	features: List<Feature>? = null,
	new: Set<Feature> = emptySet()
) {
	val catalog = koinInject<FeatureCatalog>()
	val preferenceManager = koinInject<PreferenceManager>()
	val navidrome = koinInject<NavidromeManager>()
	val backStack = LocalNavStack.current
	val info by produceState<ServerInfo?>(null) { value = navidrome.serverInfo() }
	val colors = MaterialTheme.colorScheme

	Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
		for (feature in features ?: Feature.entries.filter(catalog::onPlatform)) {
			val setUp = catalog.isSetUp(feature)
			val title = stringResource(featureTitle(feature))
			val isNew = feature in new
			when (feature) {
				Feature.SmartPlaylists -> {
					val canSmart = info?.canEditSmartPlaylists == true
					FeatureCard(
						icon = Icons.Outlined.PlaylistAddCheck,
						iconColor = colors.secondary,
						iconContentColor = colors.onSecondary,
						title = title,
						isNew = isNew,
						description = stringResource(Res.string.info_feature_smart_playlists),
						setUp = canSmart && setUp,
						checked = canSmart && setUp,
						enabled = canSmart,
						onClick = { preferenceManager.smartPlaylistsEnabled = !setUp }
					) {
						info?.let { info ->
							if (canSmart) {
								StatusLine(info.label.orEmpty(), done = setUp)
							} else UsesLine(stringResource(Res.string.info_smart_playlists_unavailable))
						}
					}
				}
				Feature.AudioMuse -> FeatureCard(
					icon = Icons.Outlined.Soundwave,
					iconColor = colors.tertiary,
					iconContentColor = colors.onTertiary,
					title = title,
					isNew = isNew,
					description = stringResource(Res.string.info_feature_audiomuse),
					setUp = setUp,
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.AudioMuse) }
				) {
					if (setUp) StatusLine(stringResource(Res.string.info_audiomuse_set_up), done = true)
					else UsesLine(stringResource(Res.string.info_audiomuse_uses))
				}
				Feature.Discover -> FeatureCard(
					icon = Icons.Outlined.Explore,
					iconColor = colors.primary,
					iconContentColor = colors.onPrimary,
					title = title,
					isNew = isNew,
					description = stringResource(Res.string.info_feature_discover),
					setUp = setUp,
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.Lidarr) }
				) {
					when {
						!setUp -> UsesLine(
							stringResource(Res.string.info_discover_uses),
							Icons.Brand.Lidarr, Icons.Brand.Deezer
						)
						preferenceManager.listenBrainzUsername.isNotBlank() ->
							StatusLine(stringResource(Res.string.info_discover_set_up_listenbrainz), done = true)
						else -> StatusLine(stringResource(Res.string.info_discover_set_up), done = true)
					}
				}
				Feature.ListeningHistory -> FeatureCard(
					icon = Icons.Outlined.History,
					iconColor = colors.primaryContainer,
					iconContentColor = colors.onPrimaryContainer,
					title = title,
					isNew = isNew,
					description = stringResource(Res.string.info_feature_history),
					setUp = setUp,
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.ListeningHistory) }
				) {
					if (setUp) StatusLine(
						stringResource(
							Res.string.info_history_set_up,
							stringResource(historySourceLabel(preferenceManager.listeningHistorySource))
						),
						done = true
					) else UsesLine(stringResource(Res.string.info_history_uses), Icons.Brand.Lastfm)
				}
				Feature.NewAlbumAlerts -> {
					val alerts = rememberNewAlbumAlerts()
					FeatureCard(
						icon = Icons.Outlined.Notifications,
						iconColor = colors.surfaceContainerHighest,
						iconContentColor = colors.onSurfaceVariant,
						title = title,
						isNew = isNew,
						description = stringResource(Res.string.info_feature_new_album_alerts),
						setUp = setUp,
						checked = setUp,
						onClick = { alerts.set(!setUp) }
					) {
						if (alerts.denied) UsesLine(stringResource(Res.string.info_notifications_disabled))
					}
				}
			}
		}
	}
}

/** What [feature] is called, on its card and wherever a list names it. */
fun featureTitle(feature: Feature): StringResource = when (feature) {
	Feature.SmartPlaylists -> Res.string.option_smart_playlists
	Feature.AudioMuse -> Res.string.title_feature_audiomuse
	Feature.Discover -> Res.string.title_feature_discover
	Feature.ListeningHistory -> Res.string.title_listening_history
	Feature.NewAlbumAlerts -> Res.string.title_feature_new_album_alerts
}

/** New album notifications, asking for the notification permission when turned on. */
class NewAlbumAlerts(val denied: Boolean, val set: (Boolean) -> Unit)

/**
 * The new-album notifications switch's state. Turning it on is the only place the permission
 * prompt shows; once denied, turning it on opens the system settings instead.
 */
@Composable
fun rememberNewAlbumAlerts(): NewAlbumAlerts {
	val preferenceManager = koinInject<PreferenceManager>()
	val permissionManager = koinInject<PermissionManager>()
	val scope = rememberCoroutineScope()
	var denied by remember { mutableStateOf(false) }
	return NewAlbumAlerts(denied) { on ->
		when {
			!on -> {
				preferenceManager.newAlbumsNotifications = false
				denied = false
			}
			denied -> permissionManager.openPermissionsSettings()
			else -> scope.launch {
				denied = !permissionManager.requestNotificationsPermission()
				preferenceManager.newAlbumsNotifications = !denied
			}
		}
	}
}

@Composable
private fun FeatureCard(
	icon: ImageVector,
	iconColor: Color,
	iconContentColor: Color,
	title: String,
	description: String,
	setUp: Boolean,
	isNew: Boolean,
	onClick: () -> Unit,
	checked: Boolean? = null,
	enabled: Boolean = true,
	status: @Composable () -> Unit = {}
) {
	val colors = MaterialTheme.colorScheme
	Surface(
		onClick = onClick,
		enabled = enabled,
		shape = MaterialTheme.shapes.largeIncreased,
		color = if (setUp) colors.secondaryContainer else colors.surfaceContainer
	) {
		Row(
			Modifier.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Surface(shape = MaterialTheme.shapes.medium, color = iconColor, contentColor = iconContentColor) {
				Icon(icon, null, Modifier.padding(10.dp).size(24.dp))
			}
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
					if (isNew) Badge(containerColor = colors.tertiary, contentColor = colors.onTertiary) {
						Text(stringResource(Res.string.action_new), Modifier.padding(horizontal = 4.dp))
					}
				}
				Text(
					description,
					style = MaterialTheme.typography.bodyMedium,
					color = colors.onSurfaceVariant
				)
				status()
			}
			// the card is the switch's target: one tap anywhere flips it
			if (checked != null) Switch(checked = checked, onCheckedChange = null, enabled = enabled)
			else Icon(Icons.Outlined.ChevronForward, null, tint = colors.onSurfaceVariant)
		}
	}
}

/** What a set-up feature is using, with a check when it is on. */
@Composable
private fun StatusLine(text: String, done: Boolean) {
	Row(
		Modifier.padding(top = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (done) Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
		Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
	}
}

/** What a feature would use, with the marks of the services that have one. */
@Composable
private fun UsesLine(text: String, vararg marks: ImageVector) {
	Row(
		Modifier.padding(top = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		for (mark in marks) Icon(mark, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
		Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
