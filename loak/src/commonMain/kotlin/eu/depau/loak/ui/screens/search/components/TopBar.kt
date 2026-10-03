package eu.depau.loak.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.generated.resources.action_search_library
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.screens.queue.QueuePaneToggle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_clear_search
import eu.depau.loak.generated.resources.action_navigate_back
import eu.depau.loak.generated.resources.title_search
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.ArrowBack
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.ui.theme.defaultFont

@Composable
fun SearchScreenTopBar(
	query: TextFieldState,
	nested: Boolean,
	onSearch: (String) -> Unit
) {
	val backStack = LocalNavStack.current

	val focusManager = LocalFocusManager.current
	val focusRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		focusRequester.requestFocus()
	}

	// wide windows: the field is a filled pill, at most 720 dp wide
	val expanded = LocalPlatformContext.current.isExpanded()
	Row(
		modifier = if (expanded) Modifier.height(72.dp).padding(end = 16.dp) else Modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		if (nested) {
			Box(
				modifier = Modifier.size(56.dp),
				contentAlignment = Alignment.Center
			) {
				IconButton(
					onClick = {
						focusManager.clearFocus(true)
						if (backStack.size > 1) backStack.removeLastOrNull()
					}
				) {
					Icon(
						Icons.Outlined.ArrowBack,
						contentDescription = stringResource(Res.string.action_navigate_back),
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
		BasicTextField(
			state = query,
			modifier = Modifier
				.weight(1f, fill = !expanded)
				.then(
					if (expanded) Modifier
						.padding(start = if (nested) 0.dp else 16.dp)
						.widthIn(max = 720.dp)
						.fillMaxWidth()
						.height(56.dp)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
						.padding(start = 20.dp, end = 8.dp)
					else Modifier
						.height(72.dp)
						.padding(start = if (nested) 0.dp else 18.dp)
				)
				.focusRequester(focusRequester),
			lineLimits = TextFieldLineLimits.SingleLine,
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
			onKeyboardAction = {
				focusManager.clearFocus()
				if (query.text.isNotBlank()) {
					onSearch(query.text.toString())
				}
			},
			textStyle = TextStyle(
				color = MaterialTheme.colorScheme.onSurface,
				fontFamily = defaultFont()
			),
			cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
			decorator = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					if (expanded) Icon(
						Icons.Outlined.Search,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
						if (query.text.isEmpty()) {
							Text(
								text = stringResource(
									if (expanded) Res.string.action_search_library else Res.string.title_search
								),
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						innerTextField()
					}
					if (expanded && query.text.isNotEmpty()) ClearButton(query)
				}
			}
		)
		if (expanded) QueuePaneToggle()
		if (!expanded) Box(
			modifier = Modifier.size(56.dp),
			contentAlignment = Alignment.Center
		) {
			if (query.text.isNotEmpty()) ClearButton(query, Modifier.padding(horizontal = 8.dp))
		}
	}
}

@Composable
private fun ClearButton(query: TextFieldState, modifier: Modifier = Modifier) {
	IconButton(modifier = modifier, onClick = { query.clearText() }) {
		Icon(
			Icons.Outlined.Close,
			contentDescription = stringResource(Res.string.action_clear_search)
		)
	}
}
