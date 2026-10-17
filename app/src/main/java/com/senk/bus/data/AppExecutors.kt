package com.senk.bus.data

import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single-threaded executor for disk/database work.
 *
 * The worker thread is named for easier debugging and has an uncaught-exception handler so a
 * failure is logged instead of silently killing the process.
 */
object AppExecutors {

    private const val TAG = "AppExecutors"

    private val diskIOExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bus-disk-io").apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { failedThread, error ->
                Log.e(TAG, "Uncaught exception on ${failedThread.name}", error)
            }
        }
    }

    @JvmStatic
    fun diskIO(runnable: Runnable) {
        diskIOExecutor.execute(runnable)
    }
}
