package eu.depau.loak.androidApp

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.androidApp.di.AndroidResourceProvider
import eu.depau.loak.di.addSentryBreadcrumb
import eu.depau.loak.di.ActivityProvider
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.di.initKoin
import eu.depau.loak.di.initializeSentry
import eu.depau.loak.domain.manager.scheduleDownloads
import kotlin.system.exitProcess

class Application : android.app.Application() {
	override fun onCreate() {
		super.onCreate()

		if (isCrashProcess()) {
			return
		}

		Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
			try {
				val intent = Intent(this, CrashActivity::class.java).apply {
					putExtra("stacktrace", Log.getStackTraceString(throwable))
					flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
				}
				startActivity(intent)
			} catch (e: Exception) {
				Log.e("Application", "failed to start CrashActivity", e)
			} finally {
				exitProcess(1)
			}
		}

		// After the crash-screen handler so Sentry's own uncaught-exception
		// integration chains to it (report → crash screen → exit) instead of
		// being replaced wholesale by Thread.setDefaultUncaughtExceptionHandler.
		initializeSentry(
			release = packageManager
				.getPackageInfo(packageName, 0)
				.versionName.toString()
		)

		initKoin {
			modules(module(createdAtStart = true) {
				singleOf(::ActivityProvider)
				single<ResourceProvider> {
					AndroidResourceProvider()
				}
			})
			androidContext(this@Application)
			androidLogger()
		}
		scheduleDownloads(this)
		addSentryBreadcrumb("startup", "koin_ready")

		// Android Automotive draws media apps itself: there the icon opens the car's media app
		// (library, queue, usable while driving), not this UI, which it blocks while driving; the
		// UI stays reachable parked from that app's settings button. The swap keeps one launcher
		// entry, or Android adds an app-info one. Elsewhere, DEFAULT also undoes the disable the
		// removed Inverted icon alias left behind.
		fun setState(component: String, state: Int) = packageManager.setComponentEnabledSetting(
			ComponentName(this, component), state, PackageManager.DONT_KILL_APP
		)
		val phoneEntry = "$packageName.MainActivityDefault"
		if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
			// enable first: the playback service opens whichever launcher entry is on
			setState(CarLauncherActivity::class.java.name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
			setState(phoneEntry, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
		} else {
			setState(phoneEntry, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
		}
	}

	private fun isCrashProcess(): Boolean {
		val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getProcessName()
		} else {
			val pid = android.os.Process.myPid()
			val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
			am.runningAppProcesses?.find { it.pid == pid }?.processName
		}
		return processName?.endsWith(":crash") == true
	}
}
