package com.threethan.launchercore.util;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** @noinspection unused*/
public abstract class App {
    private static final Map<String, Type> packageTypeCache = new ConcurrentHashMap<>();

    public enum Type {PHONE, VR, TV, PANEL, SHORTCUT, WEB, UNSUPPORTED, UTILITY}

    /**
     * Check if a given package exists and is installed on the device
     * @param packageName Name of the package
     * @return True if the given package exists and could be queried
     */
    public static boolean packageExists(String packageName) {
        try {
            Core.context().getPackageManager().getApplicationInfo(packageName,0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    /**
     * Gets the type of an app
     * @param app ApplicationInfo object for the app
     * @return Type of the app
     */
    public static Type getType(ApplicationInfo app) {
        if (packageTypeCache.containsKey(app.packageName))
            return packageTypeCache.get(app.packageName);
        SettingsManager.sortableLabelCache.remove(app.packageName);
        Type type = getTypeInternal(app);
        packageTypeCache.put(app.packageName, type);
        if (!app.enabled || Platform.excludedPackageNames.contains(app.packageName)
                || app.packageName.startsWith(Core.context().getPackageName())
                || (type == Type.TV || type == Type.PHONE) && Launch.getLaunchIntent(app) == null) {
            packageTypeCache.put(app.packageName, Type.UNSUPPORTED);
            return Type.UNSUPPORTED;
        }
        return type;
    }
    /**
     * Gets the type of an app (which was previously queried)
     * @param packageName Package name of the app
     * @return Type of the app
     */
    public static Type getType(String packageName) {
        if (packageTypeCache.containsKey(packageName))
            return packageTypeCache.get(packageName);
        Log.w(Core.TAG, "Queried package "+packageName+" whose type is not yet known");
        return Type.PHONE;
    }
    private static Type getTypeInternal(ApplicationInfo app) {
        if (!app.enabled) return Type.UNSUPPORTED;
        if (app instanceof UtilityApplicationInfo) return Type.UTILITY;
        if (isWebsite(app.packageName)) return Type.WEB;
        if (isShortcut(app.packageName)) return Type.SHORTCUT;
        if (isPanelApp(app)) return Type.PANEL;
        if (isVrApp(app)) return Type.VR;
        if (isTvApp(app)) return Type.TV;
        return Type.PHONE;
    }
    private static boolean isPanelApp(ApplicationInfo app) {
        if (app.packageName.startsWith("systemux://")) return true;
        PackageManager pm = Core.context().getPackageManager();
        Intent panelIntent = new Intent("com.oculus.vrshell.SHELL_MAIN");
        panelIntent.setPackage(app.packageName);
        // Detect panel apps
        if (pm.resolveService(panelIntent, 0) != null) return true;
        // Detect Oculus PWAs
        return app.metaData != null && app.metaData.containsKey("com.oculus.pwa.START_URL");
    }
    private static boolean isVrApp(ApplicationInfo app) {
        if (isPanelApp(app)) return false;
        if (app.packageName.equals("com.android.settings")) return false;
        if (app.metaData != null)
            if ( app.metaData.containsKey("com.oculus.ossplash")
                || app.metaData.containsKey("com.samsung.android.vr.application.mode")
                || app.metaData.containsKey("com.oculus.intent.category.VR"))
                return true;

        PackageManager pm = Core.context().getPackageManager();

        // Additional check since we probably can't get metadata
        if (PlatformExt.isMetastoreBuild()) {
            Intent li = new Intent(Intent.ACTION_MAIN);
            li.addCategory(Intent.CATEGORY_LAUNCHER);
            li.setPackage(app.packageName);
            boolean liResolves = pm.resolveActivity(li, 0) != null;
            Intent ii = new Intent(Intent.ACTION_MAIN);
            ii.addCategory(Intent.CATEGORY_INFO);
            ii.setPackage(app.packageName);
            boolean iiResolves = pm.resolveActivity(ii, 0) != null;
            if (iiResolves && !liResolves) {
                // If it resolves to info but not launcher, it's probably an older unity VR app
                return true;
            }
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory("com.oculus.intent.category.VR");
        intent.setPackage(app.packageName);
        return pm.resolveActivity(intent, 0) != null;
    }
    private static boolean isTvApp(ApplicationInfo app) {
        if (Platform.isVr()) return false;
        PackageManager pm = Core.context().getPackageManager();
        // First check for banner, then check for intent
        if (app.banner != 0) return true;
        Intent tvIntent = new Intent();
        tvIntent.setAction(Intent.CATEGORY_LEANBACK_LAUNCHER);
        tvIntent.setPackage(app.packageName);
        return (tvIntent.resolveActivity(pm) != null);
    }

    public static boolean isWebsite(String packageName) {
        return packageName.contains("://")
                && !packageName.startsWith("systemux:")
                && !isShortcut(packageName);
    }
    public static boolean isShortcut(String packageName) {
        return packageName.startsWith("{\"mActivity\"")
                || packageName.startsWith("json://");
    }

    /** @return The string label for an app */
    public static String getLabel(ApplicationInfo app) {
        return SettingsManager.getAppLabel(app);
    }
    private static final Map<String, String> mmdLabelCache = new ConcurrentHashMap<>();

    /** Get the string label for an app async */
    public static void getLabel(ApplicationInfo app, Consumer<String> onLabel) {
        SettingsManager.getAppLabel(app, onLabel);
    }
    /** @noinspection unused*/
    public static boolean isBanner(ApplicationInfo app) {
        return SettingsManager.getAppIsBanner(app);
    }

    /** @return Application info for the given packageName (*without metadata) */
    public static ApplicationInfo infoFor(String packageName) {
        try {
            return Core.context().getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(Core.TAG, "Could not find application info for "+packageName);
            ApplicationInfo info = new ApplicationInfo();
            info.packageName = packageName;
            return info;
        }
    }

    public static void clearCache() {
        packageTypeCache.clear();
        for (ApplicationInfo app : Platform.listInstalledApps()) App.getType(app);
    }
}

