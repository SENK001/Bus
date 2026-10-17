package com.senk.bus.data;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import android.util.Log;

/**
 * Single-threaded executor for disk/database work.
 *
 * <p>The worker thread is named for easier debugging and has an uncaught-exception handler so a
 * failure is logged instead of silently killing the process.
 */
public final class AppExecutors {

    private static final String TAG = "AppExecutors";

    private static final ExecutorService DISK_IO =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "bus-disk-io");
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                            Log.e(TAG, "Uncaught exception on " + failedThread.getName(), error));
                    return thread;
                }
            });

    private AppExecutors() {
    }

    public static void diskIO(Runnable runnable) {
        DISK_IO.execute(runnable);
    }
}
