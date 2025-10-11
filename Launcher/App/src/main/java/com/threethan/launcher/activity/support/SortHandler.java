package com.threethan.launcher.activity.support;

import android.content.pm.ApplicationInfo;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launchercore.util.App;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SortHandler {
    private final SettingsManager settingsManager;
    public SortHandler(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }
    public enum SortMode {
        STANDARD,
        RECENTLY_USED,
        RECENTLY_INSTALLED,
        SEARCH;

        public @Nullable String getDisplayText(LauncherActivity launcherActivity) {
            boolean hasUsage = PlaytimeHelper.hasUsagePermission();
            return switch (this) {
                case STANDARD -> launcherActivity.getString(R.string.sort_standard);
                case RECENTLY_USED -> launcherActivity.getString(hasUsage ? R.string.sort_recently_used : R.string.sort_recently_used_no_perm);
                case RECENTLY_INSTALLED -> launcherActivity.getString(hasUsage ? R.string.sort_recently_installed : R.string.sort_recently_installed_no_perm);
                case SEARCH -> null; // No display name for search mode
            };
        }
    }
    /**
     * Gets the visible apps to show in the app grid
     * @param selectedGroups If true, only return apps in currently selected groups
     * @param allApps A collection of all apps
     * @return Apps which should be shown
     */
    public List<ApplicationInfo>
    getVisibleAppsSorted(List<String> selectedGroups, Collection<ApplicationInfo> allApps, SortMode sortMode) {
        List<ApplicationInfo> apps = settingsManager.getVisibleApps(selectedGroups, allApps);

        switch (sortMode) {
            case SEARCH -> {
                // Standard sort (by raw label)
                // Must be set here, else labels might async load during sort which causes issues
                Map<ApplicationInfo, String> labels = new HashMap<>();
                apps.forEach(app -> labels.put(app, SettingsManager.getAppLabel(app)));

                apps.sort(Comparator.comparing(labels::get));
            }
            case STANDARD -> {
                // Standard sort (by label)
                // Must be set here, else labels might async load during sort which causes issues
                Map<ApplicationInfo, String> labels = new HashMap<>();
                apps.forEach(app -> labels.put(app, SettingsManager.getSortableAppLabel(app)));

                apps.sort(Comparator.comparing(labels::get));
            }
            case RECENTLY_USED -> {
                // Recently used
                Map<ApplicationInfo, Long> lastUsed = new HashMap<>();

                if (PlaytimeHelper.hasUsagePermission()) {
                    apps.forEach(app -> lastUsed.put(app, Math.max(SettingsManager.getAppLastLaunchedTime(app.packageName), PlaytimeHelper.getLastOpenedTime(app.packageName))));
                } else {
                    apps.forEach(app -> lastUsed.put(app, SettingsManager.getAppLastLaunchedTime(app.packageName)));
                }

                //noinspection DataFlowIssue
                apps.sort(Comparator.comparingLong(a -> lastUsed.getOrDefault((ApplicationInfo) a, 0L)).reversed());

            }
            case RECENTLY_INSTALLED -> {
                // Recently installed
                Map<ApplicationInfo, Long> timeInstalled = new HashMap<>();

                if (PlaytimeHelper.hasUsagePermission()) {
                    apps.forEach(app -> timeInstalled.put(app, PlaytimeHelper.getInstalledTime(app.packageName)));
                } else {
                    apps.forEach(app -> timeInstalled.put(app, SettingsManager.getAppAddedTime(app.packageName)));
                }

                //noinspection DataFlowIssue
                apps.sort(Comparator.comparingLong(a -> timeInstalled.getOrDefault((ApplicationInfo) a, 0L)).reversed());

            }
        }

        // Divide by banner/icon
        apps.sort((a, b) -> {
            boolean aIsBanner = App.isBanner(a);
            boolean bIsBanner = App.isBanner(b);
            if (aIsBanner && !bIsBanner) return -1;
            if (!aIsBanner && bIsBanner) return 1;
            return 0;
        });

        // Sort Done!
        return apps;
    }
}
