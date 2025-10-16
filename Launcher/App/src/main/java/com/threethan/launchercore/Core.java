package com.threethan.launchercore;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Core {
    private static WeakReference<Context> context;
    public static final String TAG = "Launcher Core";
    private static final List<Runnable> onReadyRunnableList = new LinkedList<>();
    /**
     * Initializes launcher core,
     * has no effect after the first call unless context object is destroyed
     * @param context Any context object from this app
     */
    public static void init(Context context) {
        Core.context = new WeakReference<>(context.getApplicationContext());
        // Get and cache all app types asynchronously
        for (Runnable runnable : onReadyRunnableList) runnable.run();

    }
    /** @return The current application context */
    public static Context context() {
        if (context == null) throw new RuntimeException(
                "LauncherCore was used without first calling LauncherCore.init(context)"
        );
        if (context.get() == null) throw new RuntimeException(
                "LauncherCore was used after it's context was cleared!"
        );
        return context.get();
    }
    /** Runs the runnable once the core context has been init. Should be called statically. */
    public static synchronized void whenReady(Runnable run) {
        if (context != null) run.run();
        onReadyRunnableList.add(run);
    }

    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
}
