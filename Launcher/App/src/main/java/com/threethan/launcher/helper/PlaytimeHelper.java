package com.threethan.launcher.helper;


import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.util.CustomDialog;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Get basic info related to app/game play time */
public abstract class PlaytimeHelper {
    private static Long getTotalSeconds(String pkgName) {
        if (!hasUsagePermission()) return 0L;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 5);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -15);
        long startTime = calendar.getTimeInMillis();
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                Core.context().getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> usageStatsList
                = usageStatsManager.queryUsageStats(3, startTime, endTime);
        usageStatsList.removeIf(usageStats -> !usageStats.getPackageName().equals(pkgName));
        long total = 0;
        for (UsageStats stats : usageStatsList) total += stats.getTotalTimeInForeground() / 1000;
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
        if (!hasUsagePermission()) return 0L;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 5);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -15);
        long startTime = calendar.getTimeInMillis();
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                Core.context().getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> usageStatsList
                = usageStatsManager.queryUsageStats(3, startTime, endTime);
        usageStatsList.removeIf(usageStats -> !usageStats.getPackageName().equals(pkgName));
        long lastUsed = 0;
        for (UsageStats stats : usageStatsList)
            if (stats.getLastTimeUsed() > lastUsed) lastUsed = stats.getLastTimeUsed();
        return lastUsed;
    }
    public static Long getInstalledTime(String pkgName) {
        if (!hasUsagePermission()) return 0L;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 5);
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -15);
        long startTime = calendar.getTimeInMillis();
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                Core.context().getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> usageStatsList
                = usageStatsManager.queryUsageStats(3, startTime, endTime);
        usageStatsList.removeIf(usageStats -> !usageStats.getPackageName().equals(pkgName));
        long firstInstall = Long.MAX_VALUE;
        for (UsageStats stats : usageStatsList)
            if (stats.getFirstTimeStamp() < firstInstall) firstInstall = stats.getFirstTimeStamp();
        if (firstInstall == Long.MAX_VALUE) return 0L;
        return firstInstall;
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
        //noinspection deprecation
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
                BasicDialog.toast("Usage permission settings are unavailable");
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
                    BasicDialog.toast(Core.context().getString(R.string.playtime_charts_warn));
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
