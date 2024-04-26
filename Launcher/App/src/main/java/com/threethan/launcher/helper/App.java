package com.threethan.launcher.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.threethan.launcher.R;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.support.SettingsManager;

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
public abstract class App {
    private static final String VR_INTENT_CATEGORY = "com.oculus.intent.category.VR";
    static Map<Type, Set<String>> categoryIncludedApps = new HashMap<>();
    static Map<Type, Set<String>> categoryExcludedApps = new HashMap<>();

    public enum Type {
        TYPE_PHONE, TYPE_VR, TYPE_TV, TYPE_PANEL, TYPE_WEB, TYPE_SUPPORTED, TYPE_UNSUPPORTED
    }
    private static boolean checkVirtualReality(ApplicationInfo applicationInfo) {
        if (applicationInfo.metaData == null) return false;
        if (applicationInfo.metaData.containsKey("com.oculus.supportedDevices")) return true;
        if (applicationInfo.metaData.containsKey("com.oculus.ossplash")) return true;
        if (applicationInfo.metaData.containsKey("com.samsung.android.vr.application.mode")) return true;

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(VR_INTENT_CATEGORY);
        intent.setPackage(applicationInfo.packageName);

        PackageManager pm = LauncherActivity.getAnyInstance().getPackageManager();
        return pm.resolveActivity(intent, 0) != null;
    }
    private static boolean checkAndroidTv
            (ApplicationInfo applicationInfo) {
        PackageManager pm = LauncherActivity.getAnyInstance().getPackageManager();
        // First check for banner
        if (applicationInfo.banner != 0) return true;
        // Then check for intent
        Intent tvIntent = new Intent();
        tvIntent.setAction(Intent.CATEGORY_LEANBACK_LAUNCHER);
        tvIntent.setPackage(applicationInfo.packageName);
        return (tvIntent.resolveActivity(pm) != null);

    }
    private static Set<String> nonNull(Set<String> set) {
        if (set == null) return new HashSet<>();
        else return set;
    }
    public static boolean isAppOfType
            (ApplicationInfo applicationInfo, App.Type appType) {

        final LauncherActivity launcherActivity = LauncherActivity.getAnyInstance();

        if (!categoryIncludedApps.containsKey(appType)) {
            // Create new hashsets for cache
            categoryIncludedApps.put(appType, Collections.synchronizedSet(new HashSet<>()));
            categoryExcludedApps.put(appType, Collections.synchronizedSet(new HashSet<>()));
        }

        // Check cache
        if (nonNull(categoryIncludedApps.get(appType))
                .contains(applicationInfo.packageName)) return true;
        if (nonNull(categoryExcludedApps.get(appType))
                .contains(applicationInfo.packageName)) return false;

        boolean isType = switch (appType) {
            case TYPE_VR -> checkVirtualReality(applicationInfo);
            case TYPE_TV -> checkAndroidTv(applicationInfo);
            case TYPE_PANEL -> checkPanelApp(applicationInfo, launcherActivity);
            case TYPE_WEB -> isWebsite(applicationInfo);
            case TYPE_PHONE -> true;
            case TYPE_SUPPORTED -> checkSupported(applicationInfo, launcherActivity);
            default -> false;

            // this function shouldn't be called until checking higher priorities first
        };

        if (isType)
            nonNull(categoryIncludedApps.get(appType)).add(applicationInfo.packageName);
        else
            nonNull(categoryExcludedApps.get(appType)).add(applicationInfo.packageName);

        return isType;
    }


    private static boolean checkPanelApp
            (ApplicationInfo applicationInfo, LauncherActivity launcherActivity) {
        if (applicationInfo instanceof PanelApp &&
                AppData.getFullPanelAppList().contains((PanelApp) applicationInfo)) return true;

        if (AppData.AUTO_DETECT_PANEL_APPS) {
            PackageManager pm = launcherActivity.getPackageManager();
            Intent panelIntent = new Intent("com.oculus.vrshell.SHELL_MAIN");
            panelIntent.setPackage(applicationInfo.packageName);
            if (pm.resolveService(panelIntent, 0) != null) return true; // Detect panel apps
            return applicationInfo.metaData != null &&
                    applicationInfo.metaData.containsKey("com.oculus.pwa.START_URL"); // Detect Oculus PWAs
        } else return false;
    }

    synchronized public static boolean isSupported(ApplicationInfo app) {
        return isAppOfType(app, Type.TYPE_SUPPORTED);
    }
    private static String[] unsupportedPrefixes;
    private static boolean checkSupported(ApplicationInfo app, LauncherActivity launcherActivity) {
        if (isWebsite(app)) return true;

        if (unsupportedPrefixes == null)
            unsupportedPrefixes = launcherActivity.getResources().getStringArray(R.array.unsupported_app_prefixes);
        for (String prefix : unsupportedPrefixes)
            if (app.packageName.startsWith(prefix))
                return false;

        if (app.metaData != null)
            if (app.metaData.keySet().contains("com.oculus.environmentVersion"))
                return isAppOfType(app, Type.TYPE_VR);

        return Launch.checkLaunchable(launcherActivity, app);
    }
    public static boolean isBanner(ApplicationInfo applicationInfo) {
        return typeIsBanner(getType(applicationInfo));
    }
    /** @noinspection SuspiciousMethodCalls*/
    public static boolean isWebsite(ApplicationInfo applicationInfo) {
        return (isWebsite(applicationInfo.packageName) &&
                !AppData.getFullPanelAppList().contains(applicationInfo));
    }
    public static boolean isWebsite(String packageName) {
        return (packageName.contains("//"));
    }
    public static boolean isShortcut(ApplicationInfo applicationInfo) {
        return isShortcut(applicationInfo.packageName);
    }
    public static boolean isShortcut(String packageName) {
        return packageName.startsWith("{\"mActivity\"") || packageName.startsWith("json://");
    }

    // Invalidate the values caches for isBlank functions
    public static synchronized void invalidateCaches() {
        categoryIncludedApps = new HashMap<>();
        categoryExcludedApps = new HashMap<>();
    }
    // Opens the app info settings pane
    public static void openInfo(Context context, String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" +
                packageName.replace(PanelApp.packagePrefix, "")));
        context.startActivity(intent);
    }
    // Requests to uninstall the app
    public static void uninstall(String packageName) {
        LauncherActivity launcher = LauncherActivity.getAnyInstance();
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

    public static App.Type getType(ApplicationInfo app) {
        for (Type type : Platform.getSupportedAppTypes(LauncherActivity.getAnyInstance())) {
            if (isAppOfType(app, type)) return type;
        }
        return Type.TYPE_UNSUPPORTED;
    }

    public static String getTypeString(Activity a, Type type) {
        return switch (type) {
            case TYPE_PHONE -> a.getString(R.string.apps_phone);
            case TYPE_VR -> a.getString(R.string.apps_vr);
            case TYPE_TV -> a.getString(R.string.apps_tv);
            case TYPE_WEB -> a.getString(R.string.apps_web);
            case TYPE_PANEL -> a.getString(R.string.apps_panel);
            default -> "Invalid type";
        };
    }

    public static String getDefaultGroupFor(App.Type type) {
        return SettingsManager.getDefaultGroupFor(type);
    }
    public static boolean typeIsBanner(App.Type type) {
        return SettingsManager.isTypeBanner(type);
    }

    public static boolean isPackageEnabled(Activity activity, String packageName) {
        try {
            ApplicationInfo ai = activity.getPackageManager().getApplicationInfo(packageName,0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
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
