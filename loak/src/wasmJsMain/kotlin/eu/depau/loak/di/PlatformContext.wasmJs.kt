package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize

@JsFun("() => window.innerWidth")
private external fun jsInnerWidth(): Int

@JsFun("() => window.innerHeight")
private external fun jsInnerHeight(): Int

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val density = LocalDensity.current
	val width = jsInnerWidth()
	val height = jsInnerHeight()
	val sizeClass = remember(width, height, density) {
		with(density) {
			WindowSizeClass.calculateFromSize(
				DpSize(width.toDp(), height.toDp())
			)
		}
	}
	return remember(sizeClass) {
		object : PlatformContext {
			override val platformType = PlatformType.Web
			override val name = "Web"
			override val appVersion = "web"
			override val colorScheme = null
			override val sizeClass = sizeClass
		}
	}
}
