package eu.depau.loak.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun Immersive() {
	val view = LocalView.current
	DisposableEffect(view) {
		val window = view.context.activity()?.window
		val controller = window?.let { WindowCompat.getInsetsController(it, view) }
		controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		controller?.hide(WindowInsetsCompat.Type.systemBars())
		onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
	}
}

private tailrec fun Context.activity(): Activity? = when (this) {
	is Activity -> this
	is ContextWrapper -> baseContext.activity()
	else -> null
}
