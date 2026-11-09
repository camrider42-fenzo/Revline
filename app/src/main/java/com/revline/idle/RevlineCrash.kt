package com.revline.idle

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The last uncaught exception, kept in filesDir so the next launch can show it.
 * A crash on the fold closed the process before logcat was available.
 */
object RevlineCrash {
    const val FILE_NAME = "revline_crash.txt"

    private val recorded = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)
    private var directory: File? = null

    fun install(context: Context) {
        val dir = context.applicationContext.filesDir
        directory = dir
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(error, thread.name)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(error: Throwable, thread: String = Thread.currentThread().name) {
        val dir = directory ?: return
        if (!recorded.compareAndSet(false, true)) return
        runCatching {
            File(dir, FILE_NAME).writeText(buildString {
                append(thread)
                append('\n')
                append(error.stackTraceToString())
            })
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
        recorded.set(false)
    }
}
