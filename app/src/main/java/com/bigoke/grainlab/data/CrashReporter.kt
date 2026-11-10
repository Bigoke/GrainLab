package com.bigoke.grainlab.data

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a crash report to the phone when the app closes unexpectedly, and offers to share it
 * on the next launch. Nothing is sent anywhere unless the user chooses to share it.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()
                val report = buildString {
                    appendLine("GrainLab crash report")
                    appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("App version: $version")
                    appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString())
                }
                File(app.filesDir, FILE).writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingReport(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GrainLab crash report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, "Send crash report"))
    }
}
