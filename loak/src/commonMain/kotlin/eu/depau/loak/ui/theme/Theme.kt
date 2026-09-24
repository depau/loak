package eu.depau.loak.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.AnimationStyle

@Composable
fun LoakTheme(
	colorScheme: ColorScheme? = null,
	content: @Composable () -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val chosenTheme = preferenceManager.theme
	val chosenScheme = chosenTheme.colorScheme()
	val motionScheme = remember(preferenceManager.animationStyle) {
		when (preferenceManager.animationStyle) {
			AnimationStyle.Expressive -> MotionScheme.expressive()
			AnimationStyle.Standard -> MotionScheme.standard()
		}
	}
	val shapes = Shapes(
		extraSmall = ContinuousRoundedRectangle(ShapeDefaults.ExtraSmall.topStart),
		small = ContinuousRoundedRectangle(ShapeDefaults.Small.topStart),
		medium = ContinuousRoundedRectangle(ShapeDefaults.Medium.topStart),
		large = ContinuousRoundedRectangle(ShapeDefaults.Large.topStart),
		extraLarge = ContinuousRoundedRectangle(ShapeDefaults.ExtraLarge.topStart),
		largeIncreased = ContinuousRoundedRectangle(ShapeDefaults.LargeIncreased.topStart),
		extraLargeIncreased = ContinuousRoundedRectangle(ShapeDefaults.ExtraLargeIncreased.topStart),
		extraExtraLarge = ContinuousRoundedRectangle(ShapeDefaults.ExtraExtraLarge.topStart)
	)
	MaterialExpressiveTheme(
		colorScheme = colorScheme
			?: chosenScheme,
		motionScheme = motionScheme,
		typography = typography(),
		shapes = shapes,
		content = content
	)
}
