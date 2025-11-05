package com.threethan.launcher.helper;

import static com.threethan.launcher.activity.support.SettingsManager.META_LABEL_SUFFIX;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.threethan.launcher.LauncherService;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.adapter.LauncherAppsAdapter;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.lib.FileLib;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.metadata.IconLoader;
import com.threethan.launchercore.metadata.IconUpdater;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.LcDialog;
import com.threethan.launchercore.util.Platform;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * checkCompatibilityUpdate is called by the launcher when it's started, and attempts to update
 * settings and other stored values to be compatible from previous versions.
 * <p>
 * This includes calling the function to migrate from sharedPreferences.
 * <p>
 * It also provides helper functions for resetting certain types of data
 */
public abstract class Compat {
    public static final String KEY_COMPATIBILITY_VERSION = "KEY_COMPATIBILITY_VERSION";
    public static final int CURRENT_COMPATIBILITY_VERSION = 12;
    public static final boolean DEBUG_COMPATIBILITY = false;
    private static final String TAG = "Compatibility";

    public static synchronized void checkCompatibilityUpdate(LauncherActivity launcherActivity) {
        Core.init(launcherActivity);
        if (DEBUG_COMPATIBILITY) Log.e(TAG, "CRITICAL WARNING: DEBUG_COMPATIBILITY IS ON");
        DataStoreEditor dataStoreEditor = launcherActivity.getDataStoreEditor();

        int storedVersion = DEBUG_COMPATIBILITY ? 0 : dataStoreEditor.getInt(Compat.KEY_COMPATIBILITY_VERSION, -1);

        if (storedVersion == -1) {
            // Attempt migration
            DataStoreEditor dse1 = SyncCoordinator.getDefaultDataStoreEditor(launcherActivity);
            dse1.asyncWrite = false;
            dse1.migrateDefault(launcherActivity);
            DataStoreEditor dse2 = SyncCoordinator.getSortDataStore(launcherActivity);
            dse2.asyncWrite = false;
            dse2.migrateDefault(launcherActivity);
            if (dataStoreEditor.getInt(Settings.KEY_BACKGROUND, -1) != -1)
                LcDialog.toast(launcherActivity.getString(R.string.migrated));
            clearIconCache(launcherActivity);
        }

        // Update stored version in case it was migrated
        if (storedVersion == -1) {
            // Continue
            if (dataStoreEditor.getInt(Settings.KEY_BACKGROUND, -1) == -1) {
                // Store the updated version
                dataStoreEditor.putInt(Compat.KEY_COMPATIBILITY_VERSION, Compat.CURRENT_COMPATIBILITY_VERSION);
                return; // return if fresh install
            }
            storedVersion = 0; // set version to 0 if coming from a version before this system was added
        }

        if (storedVersion == Compat.CURRENT_COMPATIBILITY_VERSION) return; //Return if no update

        try {
            if (storedVersion > Compat.CURRENT_COMPATIBILITY_VERSION)
                Log.e(TAG, "Previous version greater than current!");
            // If updated
            for (int version = 0; version <= Compat.CURRENT_COMPATIBILITY_VERSION; version++) {
                if (SettingsManager.getVersionsWithBackgroundChanges().contains(version)) {
                    int backgroundIndex = dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                            Platform.isTv()
                                    ? Settings.DEFAULT_BACKGROUND_TV
                                    : Settings.DEFAULT_BACKGROUND_VR);

                    if (backgroundIndex >= 0 && backgroundIndex < SettingsManager.BACKGROUND_DARK.length)
                        dataStoreEditor.putBoolean(Settings.KEY_DARK_MODE, SettingsManager.BACKGROUND_DARK[backgroundIndex]);
                    else if (storedVersion == 0)
                        dataStoreEditor.putBoolean(Settings.KEY_DARK_MODE, Settings.DEFAULT_DARK_MODE);
                }

                switch (version) {
                    case (0):
                        if (dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                                Platform.isTv()
                                        ? Settings.DEFAULT_BACKGROUND_TV
                                        : Settings.DEFAULT_BACKGROUND_VR) == 6)
                            dataStoreEditor.putInt(Settings.KEY_BACKGROUND, -1);
                        break;
                    case (1):
                        int bg = dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                                Platform.isTv()
                                        ? Settings.DEFAULT_BACKGROUND_TV
                                        : Settings.DEFAULT_BACKGROUND_VR);
                        if (bg > 2) dataStoreEditor.putInt(Settings.KEY_BACKGROUND, bg + 1);
                        break;
                    case (2):
                        String from = dataStoreEditor.getString("KEY_DEFAULT_GROUP_VR",
                                Settings.getDefaultGroupForType(Core.context(), App.Type.VR));
                        StringLib.setPreChar(from, StringLib.STAR, true);
                        break;
                    case (3): // Should just clear icon cache, which is called anyways
                        break;
                    case (4):
                        // App launch out conversion, may not work well but isn't really important
                        // Wallpaper remap
                        int backgroundIndex = dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                                Platform.isTv()
                                        ? Settings.DEFAULT_BACKGROUND_TV
                                        : Settings.DEFAULT_BACKGROUND_VR);
                        if (backgroundIndex > 2)
                            dataStoreEditor.putInt(Settings.KEY_BACKGROUND, backgroundIndex - 1);
                    case (6):
                        // Remap old default group settings
                        final Map<String, App.Type> oldDefKeyToType = new HashMap<>();
                        oldDefKeyToType.put("KEY_DEFAULT_GROUP_2D", App.Type.PHONE);
                        oldDefKeyToType.put("KEY_DEFAULT_GROUP_VR", App.Type.VR);
                        oldDefKeyToType.put("KEY_DEFAULT_GROUP_TV", App.Type.TV);
                        oldDefKeyToType.put("KEY_DEFAULT_GROUP_WEB", App.Type.WEB);

                        for (String key : oldDefKeyToType.keySet()) {
                            String val = dataStoreEditor.getString(key, null);
                            if (val != null) {
                                dataStoreEditor.putString(Settings.KEY_DEFAULT_GROUP + oldDefKeyToType.get(key), val);
                                dataStoreEditor.removeBoolean(key);
                            }
                        }

                        final Map<String, App.Type> oldWideKeyToType = new HashMap<>();
                        oldWideKeyToType.put("KEY_WIDE_2D", App.Type.PHONE);
                        oldWideKeyToType.put("KEY_WIDE_VR", App.Type.VR);
                        oldWideKeyToType.put("KEY_WIDE_TV", App.Type.TV);
                        oldWideKeyToType.put("KEY_WIDE_WEB", App.Type.WEB);

                        for (String key : oldWideKeyToType.keySet()) {
                            if (dataStoreEditor.contains(key)) {
                                boolean val = dataStoreEditor.getBoolean(key, false);
                                dataStoreEditor.putBoolean(Settings.KEY_BANNER + oldWideKeyToType.get(key), val);
                                dataStoreEditor.removeBoolean(key);
                            }
                        }
                        break;
                    case (7):
                        clearIconCache(launcherActivity);
                    case (8):
                        // Clear old icon cache
                        for (File fromFile : Objects.requireNonNull(
                                new File (launcherActivity.getApplicationInfo().dataDir).listFiles()))
                            if (fromFile.getName().endsWith(".webp"))
                                //noinspection ResultOfMethodCallIgnored
                                fromFile.delete();
                    case (10):
                        clearIconCache(launcherActivity);
                        clearIcons(launcherActivity);
                    case (11):
                        clearIconCache(launcherActivity);
                    case (12):
                        SyncCoordinator.getPerAppDataStore(launcherActivity).copyFrom(SyncCoordinator.getDefaultDataStoreEditor(launcherActivity));
                }
            }
            Log.i(TAG, String.format("Settings Updated from v%s to v%s (Settings versions are not the same as app versions)",
                    storedVersion, Compat.CURRENT_COMPATIBILITY_VERSION));
        } catch (Exception e) {
            // This *shouldn't* fail, but if it does we should not crash
            Log.e(TAG, "An exception occurred when attempting to perform the compatibility update", e);
        }

        launcherActivity.getDataStoreEditor().removeStringSet(Settings.KEY_GROUP_APP_LIST + Settings.UNSUPPORTED_GROUP);
        launcherActivity.needsUpdateCleanup = true;

        // Store the updated version
        dataStoreEditor.putInt(Compat.KEY_COMPATIBILITY_VERSION, Compat.CURRENT_COMPATIBILITY_VERSION);
    }
    public static void doUpdateCleanup(LauncherActivity launcherActivity) {
        clearIconCache(launcherActivity);
        launcherActivity.needsUpdateCleanup = false;
    }

    // Clears all icons, including custom icons
    public static void clearIcons(LauncherActivity launcherActivity) {
        Log.i(TAG, "Icons are being cleared");
        FileLib.delete(launcherActivity.getApplicationInfo().dataDir + IconLoader.ICON_CUSTOM_FOLDER);
        launcherActivity.getDataStoreEditor().removeStringSet(Settings.KEY_FORCED_BANNER);
        launcherActivity.getDataStoreEditor().removeStringSet(Settings.KEY_FORCED_SQUARE);
        clearIconCache(launcherActivity);
    }
    // Clears all icons, except for custom icons, and sets them to be re-downloaded
    public static void clearIconCache(LauncherActivity launcherActivity) {
        clearIconCacheNoRefresh(launcherActivity);

        launcherActivity.launcherService.forEachActivity(a -> {
            if (a.getAppAdapter() != null) a.getAppAdapter().notifyAllChanged();
        });
    }
    public static void clearIconCacheNoRefresh(LauncherActivity launcherActivity) {
        Log.i(TAG, "Icon cache is being cleared");
        FileLib.delete(launcherActivity.getApplicationInfo().dataDir + IconLoader.ICON_CACHE_FOLDER);

        IconLoader.cachedIcons.clear();
        IconUpdater.nextCheckByPackageMs.clear();
    }

        // Clears any custom labels assigned to apps, including whether they've been starred
    public static void clearLabels(LauncherActivity launcherActivity) {
        Log.i(TAG, "Labels are being cleared");
        SettingsManager.appLabelCache.clear();
        SettingsManager.sortableLabelCache.clear();
        for (String packageName : launcherActivity.getAllPackages()) {
            launcherActivity.getDataStoreEditor().removeString(packageName);
            launcherActivity.getDataStoreEditor().removeString(packageName+META_LABEL_SUFFIX);
        }

        launcherActivity.launcherService.forEachActivity(LauncherActivity::refreshAppList);
    }
    // Clears the categorization of apps & resets everything to selected default groups
    public static void clearSort(LauncherActivity launcherActivity) {
        new Thread(() -> {
            Log.i(TAG, "App sort is being cleared");
            Set<String> appGroupsSet = launcherActivity.getDataStoreEditor().getStringSet(Settings.KEY_GROUPS, new HashSet<>());
            for (String groupName : appGroupsSet)
                launcherActivity.getDataStoreEditor().removeStringSet(Settings.KEY_GROUP_APP_LIST + groupName);
            SettingsManager.clearDefaultGroupsCache();
            dataStoreEditor.removeLong(Settings.KEY_NEWLY_ADDED_BASELINE); // Reset newly added baseline
            storeAndReload(launcherActivity);
        }).start();
    }
    // Resets the group list to default, including default groups for sorting
    public static void resetDefaultGroups(LauncherActivity launcherActivity) {
        new Thread(() -> {
            for (App.Type type : PlatformExt.getSupportedAppTypes())
                launcherActivity.getDataStoreEditor().removeString(Settings.KEY_DEFAULT_GROUP + type);
            SettingsManager.clearDefaultGroupsCache();
            launcherActivity.settingsManager.resetGroupsAndSort();
            clearSort(launcherActivity);
        }).start();
    }
    // Stores any settings which may have been changed then refreshes any extent launcher activities
    private static void storeAndReload(LauncherActivity launcherActivity) {
        SettingsManager.setGroupAppsMap(new ConcurrentHashMap<>());

        SettingsManager.writeGroupsAndSort();
        launcherActivity.launcherService.forEachActivity(a ->
            a.runOnUiThread(() -> {
                a.refreshAppList();
                a.forceRefreshPackages();
            }
        ));
    }
    private static DataStoreEditor dataStoreEditor;
    public static DataStoreEditor getDataStoreEditor() {
        if (LauncherActivity.getForegroundInstance() != null
        && LauncherActivity.getForegroundInstance().getDataStoreEditor() != null) {
            dataStoreEditor = LauncherActivity.getForegroundInstance().getDataStoreEditor();
        } else if (dataStoreEditor == null) {
            Log.w(TAG, "Failed to grab dataStoreEditor from instance, using fallback");
            dataStoreEditor = SyncCoordinator.getDefaultDataStoreEditor(Core.context());
        }
        if (dataStoreEditor == null)
            throw new IllegalStateException("dataStoreEditor is null!");
        return dataStoreEditor;
    }

    public static void resetIcon(ApplicationInfo app, Consumer<Drawable> callback) {
        IconLoader.cachedIcons.remove(app.packageName);
        IconUpdater.nextCheckByPackageMs.remove(app.packageName);

        File cFile = IconLoader.iconCustomFileForApp(app);
        if (cFile.exists()) //noinspection ResultOfMethodCallIgnored
            cFile.delete();

        IconLoader.loadIcon(app, d -> {
            callback.accept(d);

            LauncherActivity foregroundInstance = LauncherActivity.getForegroundInstance();
            if (foregroundInstance != null && foregroundInstance.launcherService != null) {
                foregroundInstance.launcherService.forEachActivity(a -> {
                    LauncherAppsAdapter appAdapter = a.getAppAdapter();
                    if (appAdapter != null) a.post(() -> appAdapter.notifyItemChanged(app));
                });
            }
        });
    }

    public static void restartFully() {
        int pendingId = 1330;
        Intent intent = new Intent(Core.context(), LauncherService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(Core.context(), pendingId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager mgr = (AlarmManager) Core.context().getSystemService(Context.ALARM_SERVICE);
        if (mgr != null) mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);

        LauncherActivity foregroundInstance = LauncherActivity.getForegroundInstance();
        LcDialog.closeAll();
        if (foregroundInstance != null && foregroundInstance.launcherService != null) {
            foregroundInstance.launcherService.forEachActivity(Activity::finishAffinity);
            foregroundInstance.launcherService.stopSelf();
            DelayLib.delayed(() -> System.exit(0));
        } else System.exit(0);
    }
}
