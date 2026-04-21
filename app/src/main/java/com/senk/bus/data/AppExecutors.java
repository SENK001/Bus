package com.senk.bus.data;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {
    private static final ExecutorService diskIO = Executors.newSingleThreadExecutor();

    public static void diskIO(Runnable runnable) {
        diskIO.execute(runnable);
    }
}
