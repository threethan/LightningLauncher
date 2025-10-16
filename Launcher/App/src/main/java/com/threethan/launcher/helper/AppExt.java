package com.threethan.launcher.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.LcDialog;
import com.threethan.launchercore.util.Platform;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This abstract class is provides info about applications, and helper functions for non-launching
 * intents (info, uninstall).
 * <p>
 * Functions prefixed with "check" check a property of an app using its metadata
 * Functions prefixed with "is" are wrappers around "check" functions which cache values
 */
public abstract class AppExt extends App {

    // Invalidate the values caches for isBlank functions
    public static synchronized void invalidateCaches() {
        App.clearCache();
    }
    // Opens the app info settings pane
    public static void openInfo(Context context, String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        if (PlatformExt.infoOverrides.containsKey(packageName))
            packageName = PlatformExt.infoOverrides.get(packageName);
        intent.setData(Uri.parse("package:" + packageName));
        if (Platform.isQuest()) intent.setPackage("com.android.settings");
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            LcDialog.toast("App info unavailable");
        }
    }
    // Requests to uninstall the app
    public static void uninstall(String packageName) {
        LauncherActivity launcher = LauncherActivity.getForegroundInstance();
        if (App.isWebsite(packageName)) {
            if (launcher == null) return;
            Set<String> webApps = launcher.dataStoreEditor.getStringSet(Settings.KEY_WEBSITE_LIST, Collections.emptySet());
            webApps = new HashSet<>(webApps); // Copy since we're not supposed to modify directly
            webApps.remove(packageName);
            launcher.dataStoreEditor
                    .putString(packageName, null) // set display name
                    .putStringSet(Settings.KEY_WEBSITE_LIST, webApps);
            launcher.launcherService.forEachActivity(LauncherActivity::refreshAppList);
        } else {
            if (!PlatformExt.canUninstall()) {
                LcDialog.toast("Missing uninstall permission");
                PlatformExt.validateVariantWithNotify();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("package:" + packageName));
            try {
                Core.context().startActivity(intent);
            } catch (Exception e) {
                LcDialog.toast("Could not request uninstall");
            }
            if (launcher != null) {
                for (int i = 2; i < 15; i++) // Check for uninstall completion
                    launcher.postDelayed(launcher::refreshPackages, i*1000);
            }

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

    public static boolean doesPackageExist(Context context, String packageName) {
        try {
            ApplicationInfo ignored
                    = context.getPackageManager().getApplicationInfo(packageName,0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
