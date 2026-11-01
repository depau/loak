package eu.depau.loak.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.label_wizard_step
import eu.depau.loak.ui.theme.ContinuousCapsule
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.windowDragArea
import org.jetbrains.compose.resources.stringResource

/** Where a page sits in the wizard, for the dots at its top. */
data class WizardStep(val number: Int, val of: Int)

/**
 * A setup wizard page, like a phone's own setup: step dots, a tonal icon, a big headline and
 * a line on why, the page's content, and the actions in a bar fixed at the bottom.
 */
@Composable
fun WizardScaffold(
	title: String,
	modifier: Modifier = Modifier,
	icon: ImageVector? = null,
	iconColor: Color = MaterialTheme.colorScheme.primaryContainer,
	description: String? = null,
	step: WizardStep? = null,
	actions: @Composable RowScope.() -> Unit,
	content: @Composable ColumnScope.() -> Unit
) {
	Scaffold(
		modifier = modifier,
		topBar = {
			// no app bar here, so an empty title-bar band moves the window (desktop only)
			LocalWindowChrome.current?.let {
				Spacer(Modifier.fillMaxWidth().height(it.barHeight).windowDragArea())
			}
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.consumeWindowInsets(innerPadding)
				.imePadding(),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.widthIn(max = 600.dp)
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 24.dp)
					.padding(top = 20.dp, bottom = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				step?.let { StepDots(it) }
				icon?.let {
					Surface(
						modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
						shape = MaterialTheme.shapes.large,
						color = iconColor
					) {
						Icon(it, null, Modifier.padding(14.dp).size(28.dp))
					}
				}
				Text(
					title,
					style = MaterialTheme.typography.headlineLarge,
					fontFamily = defaultFont(round = 100f)
				)
				description?.let {
					Text(
						it,
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				content()
			}
			Row(
				modifier = Modifier
					.widthIn(max = 600.dp)
					.fillMaxWidth()
					.padding(horizontal = 24.dp, vertical = 16.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
				verticalAlignment = Alignment.CenterVertically,
				content = actions
			)
		}
	}
}

/** The wizard's main action: a tall capsule, the one thing to do next. */
@Composable
fun WizardButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
	Button(
		onClick = onClick,
		enabled = enabled,
		shape = ContinuousCapsule,
		modifier = Modifier.heightIn(min = 56.dp)
	) {
		Text(
			text,
			modifier = Modifier.padding(horizontal = 12.dp),
			style = MaterialTheme.typography.titleMedium,
			fontFamily = defaultFont(round = 100f)
		)
	}
}

@Composable
private fun StepDots(step: WizardStep) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		repeat(step.of) { index ->
			val number = index + 1
			Box(
				Modifier
					.padding(end = 6.dp)
					.size(width = if (number == step.number) 28.dp else 6.dp, height = 6.dp)
					.background(
						if (number <= step.number) MaterialTheme.colorScheme.primary
						else MaterialTheme.colorScheme.outlineVariant,
						ContinuousCapsule
					)
			)
		}
		Spacer(Modifier.width(4.dp))
		Text(
			stringResource(Res.string.label_wizard_step, step.number, step.of),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}
