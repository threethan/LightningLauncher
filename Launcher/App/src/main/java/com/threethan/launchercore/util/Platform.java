package com.threethan.launchercore.util;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Build;
import android.util.Log;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launchercore.Core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** @noinspection unused */
public abstract class Platform {
    static {
        // Get package info on startup, so we can get info with only the package name
        Core.whenReady(() -> new Thread(() -> {
            for (ApplicationInfo app : Platform.listInstalledApps()) App.getType(app);
        }).start());
    }
    /**
     * @return True if running on a Meta Quest device
     */
    public static boolean isQuest() {
        return App.packageExists("com.oculus.vrshell");
    }
    /**
     * @return True if running on an Android TV device
     */
    public static boolean isTv() {
        try {
            return (((UiModeManager) Core.context().getSystemService(Context.UI_MODE_SERVICE))
                    .getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION);
        } catch (Exception ignored) {
            return false;
        }
    }
    /**
     * @return True if running on a VR headset (*currently only Meta Quest)
     */
    public static boolean isVr() {
        return isQuest();
    }
    /**
     * Gets a list of all installed apps
     * @return ApplicationInfo for each installed app (*sans exclusions)
     */
    public static List<ApplicationInfo> listInstalledApps() {
        try {
            PackageManager pm = Core.context().getPackageManager();

            // Check for QUERY_ALL_PACKAGES permission if needed (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    pm.checkPermission(android.Manifest.permission.QUERY_ALL_PACKAGES, Core.context().getPackageName())
                            != PackageManager.PERMISSION_GRANTED) {
                // Handle lack of permission if necessary (e.g., log, throw, or filter results)
                Log.w("Platform", "QUERY_ALL_PACKAGES permission not granted, filtering apps");

                Set<String> installedPackages = new HashSet<>();

                for (String category : List.of(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO,
                        "com.oculus.intent.category.VR",
                        "com.oculus.intent.category.2D",
                        "android.intent.category.DEFAULT")) {
                    Intent intent = new Intent(Intent.ACTION_MAIN, null);
                    intent.addCategory(category);

                    @SuppressLint("QueryPermissionsNeeded")
                    List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

                    for (ResolveInfo app : apps) {
                        String packageName = app.activityInfo.packageName;
                        installedPackages.add(packageName);
                    }
                }

                List<ApplicationInfo> installedApps = new ArrayList<>();
                for (String packageName : installedPackages) {
                    ApplicationInfo appInfo;
                    appInfo = App.infoFor(packageName);
                    installedApps.add(appInfo);
                }

                if (Platform.isQuest()) for (String systemUxPanelApp : questVersionedIncludedApps) {
                    ApplicationInfo panelAppInfo = new ApplicationInfo();
                    panelAppInfo.packageName = systemUxPanelApp;
                    installedApps.add(panelAppInfo);
                }

                return installedApps;

            } else {
                @SuppressLint("QueryPermissionsNeeded")
                List<ApplicationInfo> installedApps
                        = pm.getInstalledApplications(PackageManager.GET_META_DATA
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS);
                if (Platform.isQuest()) for (String systemUxPanelApp : questVersionedIncludedApps) {
                    ApplicationInfo panelAppInfo = new ApplicationInfo();
                    panelAppInfo.packageName = systemUxPanelApp;
                    installedApps.add(panelAppInfo);
                }
                return installedApps;
            }
        } catch (BadParcelableException exception) {
            Log.e("Platform", "Failed to list installed apps, retrying momentarily...", exception);
            try { Thread.sleep(250L);} catch (InterruptedException ignored) {}
            return listInstalledApps();
        }
    }

    private static final Set<String> questVersionedIncludedApps =
            Platform.getVrOsVersion() >= 81 ? Set.of(
                "systemux://aui-people-blended",
                "systemux://settings"
            ) : Set.of(
                "systemux://settings",
                "systemux://aui-social-v2",
                "systemux://file-manager",
                "systemux://sharing"
            );

    private static final Set<String> questVersionedExcludedApps =
            Platform.getVrOsVersion() >= 81 ? Set.of() : Set.of(
                    "systemux://events",
                    "systemux://file-manager",
                    "systemux://sharing",
                    "com.oculus.systemutilities",
                    "com.meta.worlds",
                    "com.oculus.metacam"
            );
    public static Map<String, String> labelOverridesCache;
    public static Map<String, String> getLabelOverrides(Context context) {
        if (labelOverridesCache == null) {
            Map<String, String> labelOverrides = new HashMap<>();
            labelOverrides.put("systemux://settings", context.getString(R.string.quest_settings));
            labelOverrides.put("systemux://aui-social-v2", context.getString(R.string.quest_people));
            labelOverrides.put("systemux://aui-people-blended", context.getString(R.string.quest_chats));
            labelOverrides.put("systemux://events", context.getString(R.string.quest_events));
            labelOverrides.put("systemux://file-manager", context.getString(R.string.quest_files));
            labelOverrides.put("systemux://sharing", context.getString(R.string.quest_camera));
            labelOverrides.put("builtin://apk-install", context.getString(R.string.util_apk_installer));
            labelOverrides.put("builtin://tv-smart-home", context.getString(R.string.util_tv_smart_home));
            labelOverrides.put("builtin://launcher-settings", context.getString(R.string.launcher_settings));
            labelOverrides.put("com.android.settings", context.getString(R.string.android_settings));
            labelOverrides.put("com.oculus.systemutilities", context.getString(R.string.quest_files));
            labelOverrides.put("com.oculus.browser", context.getString(R.string.quest_browser));
            labelOverrides.put("com.oculus.metacam", context.getString(R.string.quest_camera));
            labelOverrides.put(VariantHelper.VARIANT_SIDELOAD, context.getString(R.string.variant_sideload));
            labelOverrides.put(VariantHelper.VARIANT_METASTORE, context.getString(R.string.variant_metastore));
            labelOverrides.put(VariantHelper.VARIANT_PLAYSTORE, context.getString(R.string.variant_playstore));
            labelOverridesCache = labelOverrides;
        }
        return labelOverridesCache;
    }
    public static final Set<String> excludedPackageNames = new HashSet<>(Set.of(
            "android",
            "com.oculus.panelapp.library",
            "com.oculus.panelapp.devicepairing",
            "com.oculus.cvp",
            "com.oculus.vrshell",
            "com.oculus.shellenv",
            "com.oculus.integrity",
            "com.oculus.socialplatform",
            "com.oculus.systemactivities",
            "com.oculus.systempermissions",
            "com.oculus.systemsearch",
            "com.oculus.systemresource",
            "com.oculus.extrapermissions",
            "com.oculus.mobile_mrc_setup",
            "com.oculus.os.chargecontrol",
            "com.oculus.os.clearactivity",
            "com.oculus.os.voidactivity",
            "com.oculus.os.qrcodereader",
            "com.oculus.AccountsCenter.pwa",
            "com.oculus.identitymanage",
            "com.oculus.voidactivity",
            "com.oculus.xrstreamingclient",
            "com.oculus.vrprivacycheckup",
            "com.oculus.panelapp.settings",
            "com.oculus.panelapp.kiosk",
            "com.meta.handseducationmodule",
            "com.oculus.avatareditor",
            "com.oculus.accountscenter",
            "com.oculus.identitymanagement.service",
            "com.meta.AccountsCenter.pwa",
            "com.oculus.firsttimenux",
            "com.oculus.guidebook",
            "com.oculus.vrshell.desktop",
            "com.oculus.systemux",
            "com.android.healthconnect.controller",
            "com.android.metacam",
            "com.oculus.horizonmediaplayer", // Broken as of v78.1027
            "com.oculus.guardiansetup",
            "com.oculus.globalsearch", // Intended as part of the Navigator UI
            "com.oculus.pclinkservice.server",
            "com.meta.pclinkservice.server",

            // Google/Android TV
            "com.google.android.katniss",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.healthconnect.controller",
            "com.google.android.inputmethod.latin",
            "com.android.tv",
            "com.google.android.tvlauncher",
            "com.google.android.tv.remoteservice"
    ));

    static {
        // Add removed systemux panel apps to the excluded list
        excludedPackageNames.addAll(questVersionedExcludedApps);
    }

    /**
     * Opens the application info settings page for a given package
     * @param packageName Package name of the package
     */
    public static void openPackageInfo(String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" +
                packageName.replaceFirst("systemux://", "")));
        Core.context().startActivity(intent);
    }

    /**
     * Request to uninstall a package
     * @param packageName Package name of the package
     */
    public static void uninstall(String packageName) {
        if (App.isWebsite(packageName) || App.isShortcut(packageName)) {
            throw new RuntimeException("Uninstalling websites/shortcuts is not implemented!");
        } else {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + packageName));
            Core.context().startActivity(intent);
        }
    }

    /**
     * Gets a system property using reflection
     * @param key System property key
     * @param def Default value
     * @return Def if property doesn't exist or reflection failed, else the value of the property
     */
    public static String getSystemProperty(String key, String def) {
        try {
            @SuppressLint("PrivateApi") Class<?> systemProperties
                    = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get",
                    String.class, String.class);
            return (String) getMethod.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Gets the version of Meta's VrOs
     * @return -1 if failed, else VrOs version
     */
    public static int getVrOsVersion() {
        try {
            return Integer.parseInt(getSystemProperty("ro.vros.build.version", "-1"));
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns true if the device supports the new meta quest multi-window
     * @return True if VrOs >= 69
     */
    public static boolean supportsNewVrOsMultiWindow() {
        return getVrOsVersion() >= 69;
    }

    /**
     * Returns true if the device supports the meta quest chain-launching from 3rd party apps
     * @return True if VrOs >= 71
     */
    public static boolean supportsVrOsChainLaunch() {
        return getVrOsVersion() >= 71;
    }

    /**
     * Check if running on the Quest 3/3s
     * @return True if running on the Quest 3 or 3S
     */
    public static boolean isQuestGen3() {
        return Build.HARDWARE.equalsIgnoreCase("eureka")
                || Build.HARDWARE.equalsIgnoreCase("panther");
    }

    /**
     * Returns true if the device is unable to open android settings
     * @return False if an exported activity can be found to handle the intent
     */
    public static boolean cantLaunchSettings() {
        Log.d("Platform", "Assuming settings cant be launched");
        Intent intent = new Intent(Intent.ACTION_DEFAULT);
        intent.setPackage("com.android.settings");
        PackageManager pm = Core.context().getPackageManager();
        return intent.resolveActivity(pm) == null;
    }
    public static boolean isPhone() {
        return !isTv() && !isVr();
    }
}
