package io.github.lumiagg.gestureevo.utils

import android.app.Application
import android.content.Intent
import android.util.Log
import io.github.lumiagg.gestureevo.diagnostics.AppLogger
import kotlin.system.exitProcess

class AppCrashHandler(
    private val application: Application,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        val message = error.stackTraceToString()
        Log.e("GestureEVO Crash", "Thread = ${thread.name}", error)
        // Persist the crash before anything else: the process is about to die.
        AppLogger.crash(thread, error)
        try {
            application.startActivity(Intent(application, CrashShowActivity::class.java).apply {
                putExtra(CrashShowActivity.EXTRA_MESSAGE, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } finally {
            try {
                previousHandler?.uncaughtException(thread, error)
            } finally {
                exitProcess(1)
            }
        }
    }
}
