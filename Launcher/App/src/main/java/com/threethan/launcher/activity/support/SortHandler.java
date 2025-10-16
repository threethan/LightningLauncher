package com.threethan.launcher.activity.support;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.App;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class SortHandler {
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

    private static final ExecutorService sortExecutor = Core.EXECUTOR;
    /**
     * Gets the visible apps to show in the app grid
     * @param settingsManager Settings manager to use
     * @param selectedOnly If true, only return apps in currently selected groups
     * @param allApps A collection of all apps
     * @param sortMode Sort mode to use
     * @param onSorted Run when sorting is done
     */
    public static void
    getVisibleAppsSorted(SettingsManager settingsManager,
                         boolean selectedOnly,
                         Collection<ApplicationInfo> allApps,
                         SortMode sortMode,
                         Consumer<List<ApplicationInfo>> onSorted) {


        Consumer<List<ApplicationInfo>> onSortedWrapper = applicationInfos -> {

            // Call the callback on the main thread
            try {
                Objects.requireNonNull(LauncherActivity.getForegroundInstance()).runOnUiThread(() -> onSorted.accept(applicationInfos));
            } catch (Exception e) {
                Log.e("SortHandler", "Error calling onSorted callback", e);
            }
        };

        sortExecutor.submit(() -> getVisibleAppsSortedInternal(
                settingsManager,
                selectedOnly,
                allApps,
                sortMode,
                onSortedWrapper));
    }

    protected static void
        getVisibleAppsSortedInternal(SettingsManager settingsManager,
                                     boolean selectedOnly,
                                     Collection<ApplicationInfo> allApps,
                                     SortMode sortMode,
                                     Consumer<List<ApplicationInfo>> onSorted) {

        if (allApps.isEmpty()) {
            Log.w("SortHandler", "No apps to sort");
            onSorted.accept(List.of());
            return;
        }

        long startTime = System.currentTimeMillis();

        List<ApplicationInfo> apps = settingsManager.getVisibleApps(
                settingsManager.getAppGroupsSorted(selectedOnly),
                allApps
        );

        Consumer<List<ApplicationInfo>> onPreSorted = applicationInfos -> {
            // Divide by banner/icon
            apps.sort((a, b) -> {
                boolean aIsBanner = App.isBanner(a);
                boolean bIsBanner = App.isBanner(b);
                if (aIsBanner && !bIsBanner) return -1;
                if (!aIsBanner && bIsBanner) return 1;
                return 0;
            });
            Log.d("SortHandler", "Sorting took " + (System.currentTimeMillis() - startTime) + "ms");
            onSorted.accept(applicationInfos);
        };

        switch (sortMode) {
            case SEARCH -> {
                // Standard sort (by raw label)
                // Must be set here, else labels might async load during sort which causes issues
                Map<ApplicationInfo, String> labels = new HashMap<>();
                apps.forEach(app -> labels.put(app, SettingsManager.getAppLabel(app)));

                apps.sort(Comparator.comparing(labels::get));

                // Call synchronously to prevent search issues
                onPreSorted.accept(apps);
            }
            case STANDARD -> {
                AtomicInteger labelsLoaded = new AtomicInteger(0);
                // Standard sort (by label)
                // Must be set here, else labels might async load during sort which causes issues
                Map<ApplicationInfo, String> labels = new HashMap<>();
                SettingsManager.getSortableAppLabelsAsync(apps, (app, label) -> {
                    // On label loaded
                    labels.put(app, label);
                    if (Thread.currentThread().isInterrupted()) return;
                    if (labelsLoaded.incrementAndGet() == apps.size()) {
                        apps.sort(Comparator.comparing(labels::get));
                        // Notify all adapters to refresh
                        onPreSorted.accept(apps);
                    }
                });
            }
            case RECENTLY_USED -> {
                // Recently used
                Map<ApplicationInfo, Long> timeUsed = new HashMap<>();

                if (PlaytimeHelper.hasUsagePermission()) {
                    apps.forEach(app -> timeUsed.put(app, PlaytimeHelper.getLastOpenedTime(app.packageName)));
                    //noinspection DataFlowIssue
                    apps.sort(Comparator.comparingLong(a -> timeUsed.getOrDefault((ApplicationInfo) a, 0L)).reversed());
                    onPreSorted.accept(apps);
                } else {
                    apps.forEach(app -> SettingsManager.getAppLastLaunchedTimeAsync(app.packageName, time -> {
                        // On time loaded
                        timeUsed.put(app, time);
                        // If this is the last time, re-sort
                        if (timeUsed.size() == apps.size()) {
                            apps.sort(Comparator.comparing(timeUsed::get));
                            // Notify all adapters to refresh
                            onPreSorted.accept(apps);
                        }
                    }));
                }
            }
            case RECENTLY_INSTALLED -> {
                // Recently installed
                Map<ApplicationInfo, Long> timeInstalled = new HashMap<>();

                if (PlaytimeHelper.hasUsagePermission()) {
                    apps.forEach(app -> timeInstalled.put(app, PlaytimeHelper.getInstalledTime(app.packageName)));
                    //noinspection DataFlowIssue
                    apps.sort(Comparator.comparingLong(a -> timeInstalled.getOrDefault((ApplicationInfo) a, 0L)).reversed());
                    onPreSorted.accept(apps);
                } else {
                    apps.forEach(app -> {
                        SettingsManager.getAppAddedTimeAsync(app.packageName, time -> {
                            // On time loaded
                            timeInstalled.put(app, time);
                            // If this is the last time, re-sort
                            if (timeInstalled.size() == apps.size()) {
                                apps.sort(Comparator.comparing(timeInstalled::get));
                                // Notify all adapters to refresh
                                onPreSorted.accept(apps);
                            }
                        });
                    });
                }
            }
        }
    }
}
