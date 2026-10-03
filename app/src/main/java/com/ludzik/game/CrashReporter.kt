package com.ludzik.game

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Zapisuje informacje o awarii, żeby po ponownym uruchomieniu pokazać je na ekranie
 * (zrzut ekranu wystarczy do zdiagnozowania problemu bez kabla i logcata).
 *
 * - wyjątki Kotlin/Java: własny UncaughtExceptionHandler,
 * - awarie natywne (np. silnik 3D): ApplicationExitInfo z Androida 11+.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash"
    private const val KEY_SEEN = "seen_exit_ts"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(app.filesDir, FILE).writeText(header() + "Wątek: ${thread.name}\n\n$sw")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Zapisuje błąd, który złapaliśmy sami (aplikacja działa dalej). */
    fun record(context: Context, where: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        File(context.filesDir, FILE).writeText(header() + "Miejsce: $where\n\n$sw")
    }

    /** Raport z poprzedniego uruchomienia albo null. Raz pokazany — usuwany. */
    fun takeReport(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (f.exists()) {
            val text = f.readText()
            f.delete()
            markExitsSeen(context)
            return text
        }
        return nativeCrash(context)
    }

    private fun header() = "Ludzik 3D ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
        "Telefon: ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.HARDWARE}\n"

    private fun markExitsSeen(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SEEN, last.timestamp).apply()
    }

    /** Ostatnie natywne zakończenie procesu (np. błąd w sterowniku grafiki albo w silniku 3D). */
    private fun nativeCrash(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getLong(KEY_SEEN, 0L)
        val info = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
        if (info.timestamp <= seen) return null
        prefs.edit().putLong(KEY_SEEN, info.timestamp).apply()
        val reason = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "awaria natywna (silnik 3D / sterownik grafiki)"
            ApplicationExitInfo.REASON_CRASH -> "wyjątek"
            ApplicationExitInfo.REASON_ANR -> "aplikacja nie odpowiadała (ANR)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "brak pamięci"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "błąd inicjalizacji"
            else -> return null
        }
        val sb = StringBuilder(header())
        sb.append("Powód: $reason\n")
        info.description?.let { sb.append("Opis: $it\n") }
        // Z tombstone'a (format protobuf) wyciągamy czytelne napisy: komunikat przerwania,
        // nazwy funkcji i bibliotek z wywołań.
        if (Build.VERSION.SDK_INT >= 31 && info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            try {
                info.traceInputStream?.use { input ->
                    val bytes = input.readBytes()
                    val strings = printableStrings(bytes)
                        .filter { s ->
                            s.length >= 12 && (
                                s.contains("filament", true) || s.contains("Precondition", true) || s.contains("Panic", true) ||
                                    s.contains("abort", true) || s.contains(".so") || s.contains("Exception") || s.contains("ludzik")
                                )
                        }
                        .distinct()
                        .take(40)
                    if (strings.isNotEmpty()) sb.append("\n").append(strings.joinToString("\n"))
                }
            } catch (_: Throwable) {
            }
        }
        return sb.toString()
    }

    private fun printableStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126) {
                cur.append(c.toChar())
            } else {
                if (cur.length >= 8) out += cur.toString()
                cur.setLength(0)
            }
        }
        if (cur.length >= 8) out += cur.toString()
        return out
    }
}
