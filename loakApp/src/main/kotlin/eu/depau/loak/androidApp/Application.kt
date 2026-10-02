package eu.depau.loak.androidApp

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import eu.depau.loak.androidApp.di.AndroidResourceProvider
import eu.depau.loak.di.ActivityProvider
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.di.initKoin
import eu.depau.loak.di.initializeSentry
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
		initializeSentry()

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
