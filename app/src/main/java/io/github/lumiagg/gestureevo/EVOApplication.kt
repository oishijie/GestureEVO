package io.github.lumiagg.gestureevo

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import io.github.lumiagg.gestureevo.diagnostics.AppLogger
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.MlKitOcr
import io.github.lumiagg.gestureevo.utils.ThemeManager
import io.github.lumiagg.gestureevo.utils.AppCrashHandler
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import io.github.lumiagg.gestureevo.utils.app.syncAppCatalogPackage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first


class EvoApplication : Application() {
    private val appCatalogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appCatalogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            val packageName = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
            val pendingResult = goAsync()
            appCatalogScope.launch {
                try {
                    syncAppCatalogPackage(applicationContext, packageName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("AppCatalog", "Unable to refresh package $packageName", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(AppCrashHandler(this, previousHandler))
        AppLogger.initialize(this)
        val preferences = runBlocking { settingsStore.data.first() }
        applySavedLanguage(preferences.readKey(AppearanceSettingsActivity.KEY_APP_LANGUAGE, ""))
        runBlocking { ThemeManager.load(this@EvoApplication) }
        DynamicColorUtils.applyAppWideColors(this)
        // Settings views read the shared palette while inflating, before any service starts.
        DynamicColorUtils.refreshSurfaceColors(this)
        ContextCompat.registerReceiver(this, appCatalogReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DynamicColorUtils.refreshSurfaceColors(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            MlKitOcr.releasePreload()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MlKitOcr.releasePreload()
    }

    private fun applySavedLanguage(languageTag: String) {
        val locales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        lateinit var application: EvoApplication private set
    }
}
