package eu.depau.loak.androidApp

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import eu.depau.loak.di.SENTRY_ENABLED_KEY
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.LargeLibraryFlows
import eu.depau.loak.uitest.UserFlows
import org.koin.mp.KoinPlatform
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The user flows on the real Android app: MainActivity in the debug build, with the
 * real Application, Koin graph, database, OkHttp and ExoPlayer. The test orchestrator
 * clears the app's data before each flow (see loakApp/build.gradle.kts), so every
 * flow starts as a fresh install.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidUserFlows : UserFlows() {
	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) = runAndroidApp(flow)

	// the emulator has an audio output
	override fun playbackProgress() =
		KoinPlatform.getKoin().get<MediaPlayerViewModel>().uiState.value.progress
}

/** The large-library flows on the real Android app, like [AndroidUserFlows]. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidLargeLibraryFlows : LargeLibraryFlows() {
	// the large library's first sync alone can take a minute on an emulator
	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) = runAndroidApp(flow, 10.minutes)
}

@OptIn(ExperimentalTestApi::class)
private fun runAndroidApp(flow: suspend ComposeUiTest.() -> Unit, testTimeout: Duration = 1.minutes) =
	runAndroidComposeUiTest<MainActivity>(testTimeout = testTimeout) {
		// flows play songs: keep them silent
		InstrumentationRegistry.getInstrumentation().targetContext
			.getSystemService(AudioManager::class.java)
			.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
		flow()
	}

/** Runs the flows with crash reporting off, so test crashes never reach the real Sentry project. */
class UserFlowsRunner : AndroidJUnitRunner() {
	override fun callApplicationOnCreate(app: Application) {
		// the store Application.onCreate reads the toggle from, before Koin starts
		app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)
			.edit().putBoolean(SENTRY_ENABLED_KEY, false).commit()
		super.callApplicationOnCreate(app)
	}
}
