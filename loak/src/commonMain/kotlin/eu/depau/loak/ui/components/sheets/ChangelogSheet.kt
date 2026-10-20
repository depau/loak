package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.depau.loak.ui.theme.ContinuousCapsule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformContext
import eu.depau.loak.di.SENTRY_BUILD_COMMIT
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_dont_show_again
import eu.depau.loak.generated.resources.action_open_release
import eu.depau.loak.generated.resources.info_update
import eu.depau.loak.generated.resources.title_update
import eu.depau.loak.ui.components.common.Markdown
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.util.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class Release(
	@SerialName("tag_name") val tag: String? = null,
	@SerialName("html_url") val url: String? = null,
	@SerialName("target_commitish") val targetCommit: String? = null,
	val name: String? = null,
	val body: String? = null
)

internal data class AppVersion(
	val major: Int,
	val minor: Int,
	val patch: Int,
	val alpha: Int?
) : Comparable<AppVersion> {
	override fun compareTo(other: AppVersion): Int {
		compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
		compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
		compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
		return when {
			alpha == null && other.alpha == null -> 0
			alpha == null -> 1
			other.alpha == null -> -1
			else -> alpha.compareTo(other.alpha)
		}
	}
}

private val APP_VERSION = Regex(
	"""^v?(\d+)\.(\d+)\.(\d+)(?:-alpha\.?(\d+))?(?:[-+].*)?$"""
)

internal fun parseAppVersion(value: String): AppVersion? {
	val match = APP_VERSION.matchEntire(value) ?: return null
	return AppVersion(
		major = match.groupValues[1].toInt(),
		minor = match.groupValues[2].toInt(),
		patch = match.groupValues[3].toInt(),
		alpha = match.groupValues[4].takeIf(String::isNotEmpty)?.toInt()
	)
}

internal fun selectUpdate(
	currentVersion: String,
	buildVariant: String,
	buildCommit: String,
	releases: List<Release>
): Release? {
	if (buildVariant == "nightly") {
		return releases.firstOrNull { release ->
			release.tag == "nightly"
				&& release.targetCommit?.startsWith(buildCommit, ignoreCase = true) == false
		}
	}

	val current = parseAppVersion(currentVersion) ?: return null
	return releases
		.mapNotNull { release -> parseAppVersion(release.tag ?: "")?.let { release to it } }
		.maxByOrNull { it.second }
		?.takeIf { it.second > current }
		?.first
}

class ChangelogViewModel(
	platformContext: PlatformContext
) : ViewModel() {
	val release: StateFlow<Release?>
		field = MutableStateFlow(null)

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	init {
		checkForUpdates(
			currentVersion = platformContext.appVersion,
			buildVariant = platformContext.buildVariant,
			buildCommit = SENTRY_BUILD_COMMIT
		)
	}

	private fun checkForUpdates(
		currentVersion: String,
		buildVariant: String,
		buildCommit: String
	) {
		viewModelScope.launch {
			release.value = try {
				val nightly = buildVariant == "nightly"
				val response: HttpResponse = updateClient.get(
					if (nightly) NIGHTLY_RELEASE_URL else RELEASES_URL
				)
				if (!response.status.isSuccess()) {
					Logger.i("ChangelogViewModel", "update check failed (HTTP ${response.status.value})")
					return@launch
				}
				val releases = if (nightly) {
					listOf(response.body<Release>())
				} else {
					response.body<List<Release>>()
				}
				selectUpdate(currentVersion, buildVariant, buildCommit, releases)
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't check for updates", e)
				null
			}
		}
	}

	fun clearRelease() {
		release.value = null
	}

	private companion object {
		const val RELEASES_URL = "https://api.github.com/repos/depau/loak/releases?per_page=100"
		const val NIGHTLY_RELEASE_URL = "https://api.github.com/repos/depau/loak/releases/tags/nightly"
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet() {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val viewModel = koinViewModel<ChangelogViewModel>(
		parameters = { parametersOf(platformContext) }
	)
	val release by viewModel.release.collectAsStateWithLifecycle()
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	release?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { viewModel.clearRelease() },
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(Res.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						stringResource(Res.string.info_update, release.name ?: release.tag ?: ""),
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body ?: "",
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				Spacer(Modifier.weight(1f))

				Button(
					onClick = { release.url?.let { linkToOpen = it } },
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule
				) {
					Text(
						text = stringResource(Res.string.action_open_release),
						fontFamily = defaultFont(100)
					)
				}

				OutlinedButton(
					onClick = {
						viewModel.clearRelease()
						preferenceManager.checkForUpdates = false
					},
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule
				) {
					Text(
						text = stringResource(Res.string.action_dont_show_again),
						fontFamily = defaultFont(100)
					)
				}
			}
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
