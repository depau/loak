package eu.depau.loak.androidApp

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import eu.depau.loak.shared.PlaybackService

/**
 * The launcher entry on Android Automotive (see [Application]): opens the car's own media app on
 * Lo'ak, with the library and queue and usable while driving, instead of the phone UI.
 */
class CarLauncherActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		openCarMediaApp()
		finish()
	}
}

/** Opens Android Automotive's media app on Lo'ak. */
fun Context.openCarMediaApp() = startActivity(
	Intent("android.car.intent.action.MEDIA_TEMPLATE").putExtra(
		"android.car.intent.extra.MEDIA_COMPONENT",
		ComponentName(this, PlaybackService::class.java).flattenToString()
	)
)
