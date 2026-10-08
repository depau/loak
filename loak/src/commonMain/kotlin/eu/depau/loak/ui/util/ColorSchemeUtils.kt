package eu.depau.loak.ui.util

import eu.depau.loak.di.COVER_ART_SMALL
import eu.depau.loak.di.CoverArtId
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.ThemeMode
import eu.depau.loak.shared.MediaPlayerViewModel
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

@Composable
fun rememberColorSchemeFromCoverArt(
	coverArtId: String?,
	forceDark: Boolean = false,
	style: PaletteStyle = PaletteStyle.Content,
	specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021
): ColorScheme {
	val coilPlatformContext = LocalCoilPlatformContext.current
	val loader = koinInject<ImageLoader>()
	val model = remember(coverArtId) {
		ImageRequest.Builder(coilPlatformContext)
			.data(coverArtId?.let { CoverArtId(it, COVER_ART_SMALL) })
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.build()
	}
	val dominantColorState = rememberDominantColorState(cacheSize = 4)

	LaunchedEffect(model) {
		val result = loader.execute(model)
		result.image?.toImageBitmap()?.let { imageBitmap ->
			dominantColorState.updateFrom(imageBitmap)
		}
	}

	val preferenceManager = koinInject<PreferenceManager>()
	val inDarkTheme = isSystemInDarkTheme()
	val isDark = remember(forceDark, preferenceManager.themeMode, inDarkTheme) {
		forceDark || when (preferenceManager.themeMode) {
			ThemeMode.System -> inDarkTheme
			ThemeMode.Dark -> true
			ThemeMode.Light -> false
		}
	}
	val scheme = rememberDynamicColorScheme(
		seedColor = dominantColorState.color,
		isDark = isDark,
		style = style,
		specVersion = specVersion
	)

	return scheme
}

@Composable
fun rememberColorSchemeForCurrentSong(forceDark: Boolean = true): ColorScheme {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val coverArtId = playerState.currentSong?.coverArtId
	return rememberColorSchemeFromCoverArt(
		coverArtId = coverArtId,
		forceDark = forceDark,
		style = if (coverArtId != null) PaletteStyle.Content else PaletteStyle.Monochrome
	)
}
