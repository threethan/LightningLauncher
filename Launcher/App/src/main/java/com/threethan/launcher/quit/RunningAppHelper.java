package com.threethan.launcher.quit;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.App;

import java.util.HashMap;
import java.util.Map;

/** Finds the VR (3D) app that is currently running, using usage events */
public final class RunningAppHelper {
    /**
     * How far back to read usage events. Opening the launcher or Meta's menu over a game logs a
     * fresh PAUSED event, so this only has to cover the longest stretch of play with no menu opened.
     */
    private static final long LOOKBACK_MS = 6 * 60 * 60 * 1000L;
    private static final int ACTIVITY_STOPPED = 23;   // UsageEvents.Event.ACTIVITY_STOPPED (API 29)
    private static final int ACTIVITY_DESTROYED = 24; // UsageEvents.Event.ACTIVITY_DESTROYED (API 29)

    private RunningAppHelper() {}

    /**
     * @return The VR app that is open right now (in front, or paused behind the launcher),
     * or null if none is running or usage access is not granted. 2D apps are ignored.
     */
    @Nullable
    public static ApplicationInfo getRunningVrApp() {
        if (!PlaytimeHelper.hasUsagePermission()) {
            android.util.Log.w("QuitHelper", "No usage access; the Close pill cannot find the running app");
            return null;
        }
        Context context = Core.context();
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;

        long now = System.currentTimeMillis();
        // Nothing from before the last boot can still be running, and an app that was open when
        // the headset was turned off never logged a stop, so it would otherwise look running
        long bootTime = now - android.os.SystemClock.elapsedRealtime();
        UsageEvents events = usm.queryEvents(Math.max(now - LOOKBACK_MS, bootTime), now);
        // Tracked per activity: moving between activities of one app logs
        // "A paused, B resumed, A stopped", so the last event of the package alone may be STOPPED
        Map<String, Integer> lastType = new HashMap<>();
        Map<String, Long> lastTime = new HashMap<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == ACTIVITY_STOPPED || type == ACTIVITY_DESTROYED) {
                String key = e.getPackageName() + "/" + e.getClassName();
                lastType.put(key, type);
                lastTime.put(key, e.getTimeStamp());
            }
        }

        String best = null;
        long bestTime = 0;
        for (Map.Entry<String, Integer> entry : lastType.entrySet()) {
            int type = entry.getValue();
            if (type == ACTIVITY_STOPPED || type == ACTIVITY_DESTROYED) continue; // closed
            String key = entry.getKey();
            String pkg = key.substring(0, key.indexOf('/'));
            if (pkg.startsWith(context.getPackageName())) continue;
            if (pkg.startsWith("com.oculus.") || pkg.startsWith("com.meta.")
                    || pkg.equals("android") || pkg.startsWith("com.android.")) continue;
            Long t = lastTime.get(key);
            if (t != null && t > bestTime && isVr(context, pkg)) {
                best = pkg;
                bestTime = t;
            }
        }
        if (best == null) return null;
        try {
            return context.getPackageManager().getApplicationInfo(best, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException ex) {
            return null;
        }
    }

    private static boolean isVr(Context context, String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            return App.getType(info) == App.Type.VR;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }
}
