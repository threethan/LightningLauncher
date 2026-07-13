package com.threethan.launcher.helper;


import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.util.CustomDialog;
import com.threethan.launchercore.util.LcDialog;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Get basic info related to app/game play time */
public abstract class PlaytimeHelper {
    private static Map<String, UsageStats> cachedUsageStats;
    private static long lastCacheTimestamp = 0;

    /** Internal helper to get all usage stats, cached for 1 second. */
    private static synchronized Map<String, UsageStats> getUsageStatsMap() {
        if (!hasUsagePermission()) return null;
        long now = System.currentTimeMillis();
        if (cachedUsageStats != null && (now - lastCacheTimestamp) < 1000) {
            return cachedUsageStats;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager)
                Core.context().getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return null;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -10);
        long startTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, 11); // now + 1 year for buffer
        long endTime = calendar.getTimeInMillis();

        cachedUsageStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        lastCacheTimestamp = now;
        return cachedUsageStats;
    }

    private static Long getTotalSeconds(String pkgName) {
        Map<String, UsageStats> statsMap = getUsageStatsMap();
        if (statsMap == null) return 0L;
        UsageStats stats = statsMap.get(pkgName);
        long total = (stats == null ? 0 : stats.getTotalTimeInForeground()) / 1000;
        total += QuestGameTuner.getUsageOffset(pkgName) * 60L;
        return total;
    }
    private static final ExecutorService executorService = Core.EXECUTOR;
    public static void getPlaytime(String pkgName, Consumer<String> onTotalPlaytime) {
        if (!hasUsagePermission()) return;
        if (pkgName.equals(QuestGameTuner.PKG_NAME))
            onTotalPlaytime.accept(Core.context().getString(R.string.tuner_options));
        else executorService.submit(() -> {
            Long seconds = getTotalSeconds(pkgName);
            onTotalPlaytime.accept(formatSeconds(seconds));
        });
    }
    public static Long getLastOpenedTime(String pkgName) {
        Map<String, UsageStats> statsMap = getUsageStatsMap();
        if (statsMap == null) return 0L;
        UsageStats stats = statsMap.get(pkgName);
        return stats == null ? 0L : stats.getLastTimeUsed();
    }
    public static Long getInstalledTime(String pkgName) {
        try {
            return Core.context().getPackageManager().getPackageInfo(pkgName, 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0L;
        }
    }
    private static String formatSeconds(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 99) {
            return String.format(Locale.US, "%03dh", hours);
        } else {
            return String.format(Locale.US, "%02d:%02d", hours, minutes);
        }
    }

    public static boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) Core.context().getSystemService(Context.APP_OPS_SERVICE);
        int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), Core.context().getPackageName())
                : appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), Core.context().getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    public static void requestPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setPackage("com.android.settings");
        LauncherActivity activity = LauncherActivity.getForegroundInstance();
        if (activity != null)
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                LcDialog.toast("Usage permission settings are unavailable");
            }
        else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Core.context().startActivity(intent);
        }
    }
    /** Used to detect double presses */
    private static boolean hasJustShownToast = false;

    /** Opens an app's usage charts, if possible. Otherwise, prompts the user appropriately. */
    public static void openFor(String packageName) {
        if (hasUsagePermission()) {
            if (QuestGameTuner.isInstalled()) QuestGameTuner.chartApp(packageName);
            else {
                if (hasJustShownToast) QuestGameTuner.openInfoDialog();
                else {
                    LcDialog.toast(Core.context().getString(R.string.playtime_charts_warn));
                    hasJustShownToast = true;
                    DelayLib.delayed(() -> hasJustShownToast = false);
                }
            }
        } else {
            Context fi = LauncherActivity.getForegroundInstance();
            new CustomDialog.Builder(fi == null ? Core.context() : fi)
                    .setTitle(R.string.request_playtime_title)
                    .setMessage(R.string.request_playtime_msg)
                    .setNegativeButton(R.string.cancel, (d,v) -> d.dismiss())
                    .setPositiveButton(R.string.open_usage, (d,v) -> {
                        PlaytimeHelper.requestPermission();
                        d.dismiss();
                    }).show();
        }
    }
}
