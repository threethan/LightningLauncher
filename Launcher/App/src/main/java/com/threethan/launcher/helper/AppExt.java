package com.threethan.launcher.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.PanelApplicationInfo;
import com.threethan.launcher.data.Settings;
import com.threethan.launchercore.util.App;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This abstract class is provides info about applications, and helper functions for non-launching
 * intents (info, uninstall).
 * <p>
 * Functions prefixed with "check" check a property of an app using its metadata
 * Functions prefixed with "is" are wrappers around "check" functions which cache values
 */
public abstract class AppExt extends App {
    static Map<Type, Set<String>> categoryIncludedApps = new HashMap<>();
    static Map<Type, Set<String>> categoryExcludedApps = new HashMap<>();

    // Invalidate the values caches for isBlank functions
    public static synchronized void invalidateCaches() {
        categoryIncludedApps = new HashMap<>();
        categoryExcludedApps = new HashMap<>();
    }
    // Opens the app info settings pane
    public static void openInfo(Context context, String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" +
                packageName.replace(PanelApplicationInfo.packagePrefix, "")));
        context.startActivity(intent);
    }
    // Requests to uninstall the app
    public static void uninstall(String packageName) {
        LauncherActivity launcher = LauncherActivity.getForegroundInstance();
        if (App.isWebsite(packageName)) {
            Set<String> webApps = launcher.dataStoreEditor.getStringSet(Settings.KEY_WEBSITE_LIST, Collections.emptySet());
            webApps = new HashSet<>(webApps); // Copy since we're not supposed to modify directly
            webApps.remove(packageName);
            launcher.dataStoreEditor
                    .putString(packageName, null) // set display name
                    .putStringSet(Settings.KEY_WEBSITE_LIST, webApps);
            launcher.launcherService.forEachActivity(LauncherActivity::refreshAppList);
        } else {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + packageName));
            launcher.startActivity(intent);
        }
    }

    public static String getTypeString(Activity a, Type type) {
        return switch (type) {
            case PHONE -> a.getString(R.string.apps_phone);
            case VR -> a.getString(R.string.apps_vr);
            case TV -> a.getString(R.string.apps_tv);
            case WEB -> a.getString(R.string.apps_web);
            case PANEL -> a.getString(R.string.apps_panel);
            default -> "Invalid type";
        };
    }

    public static String getDefaultGroupFor(Type type) {
        return SettingsManager.getDefaultGroupFor(type);
    }
    public static boolean typeIsBanner(Type type) {
        return SettingsManager.isTypeBanner(type);
    }

    public static boolean doesPackageExist(Activity activity, String packageName) {
        try {
            ApplicationInfo ignored
                    = activity.getPackageManager().getApplicationInfo(packageName,0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
