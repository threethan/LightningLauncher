package com.threethan.launcher.updater;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class AddonUpdater extends RemotePackageUpdater {
    public static final String ADDON_TAG = "addons7.2.0"; // Tag for the release from which to download addons
    private static final String SHORTCUT_ADDON_VERSION = "7.2.0";

    public AddonUpdater(Activity activity) {
        super(activity);
    }

    /** --------------
     ADDON LIST
     -------------- */
    public static class Addon extends RemotePackage {

        public final String tag;
        public final Boolean isService;

        /**
         * Creates a new remotePackage with a separate release for arm64
         * @param tag Tag for equvilancy
         * @param packageName Name of the package once installed
         * @param latestVersion Latest version (string) of the package
         * @param isService True if the package is primarily an accessiblity service
         * @param url String url from which to download the package
         */

        public Addon(String tag, String packageName, String latestVersion,
                     Boolean isService, String url) {
            super(packageName, latestVersion, url);
            this.tag = tag;
            this.isService = isService;
        }

        @NonNull
        @Override
        public String toString() {
            return tag;
        }
    }
    // Package names
    public static final String TAG_LIBRARY  = "Library Shortcut Service";
    public static final String TAG_PEOPLE   = "People Shortcut Service";
    public static final String TAG_STORE   = "Store Shortcut Service";
    public static final String TAG_FEED     = "Feed Shortcut Service";
    public static final String TAG_ATV_LM   = "Android TV Launcher Manager";

    // Addons are currently on the main repo
    private static final String GIT_REPO_ADDONS = "threethan/LightningLauncher";
    private static final String URL_TEMPLATE =
            "https://github.com/"+ GIT_REPO_ADDONS +"/releases/download/%s/%s.apk";
    /**
     * Gets the URL for downloading a specific addon
     * @param fileName Name of the apk on github, excluding ".apk"
     * @return Url for downloading the addon
     */
    private static String addonUrl(String fileName) {
        return String.format(URL_TEMPLATE, ADDON_TAG, fileName);
    }

    public static final Addon[] addons = {
            // Quest-Exclusive
            new Addon(TAG_LIBRARY, "com.threethan.launcher.service.library",
                    SHORTCUT_ADDON_VERSION, true, addonUrl("ShortcutLibrary")),

            new Addon(TAG_PEOPLE, "com.threethan.launcher.service.people",
                    SHORTCUT_ADDON_VERSION, true, addonUrl("ShortcutPeople")),

            new Addon(TAG_FEED, "com.threethan.launcher.service.explore",
                    SHORTCUT_ADDON_VERSION, true, addonUrl("ShortcutFeed")),

            new Addon(TAG_STORE, "com.threethan.launcher.service.store",
                    SHORTCUT_ADDON_VERSION, true, addonUrl("ShortcutStore")),

            // AndroidTV-Exclusive
            new Addon(TAG_ATV_LM, "com.wolf.google.lm",
                    "LM (ATV) - 1.0.4", false,
                    "https://xdaforums.com/attachments/lm-atv-1-0-4-apk.5498333/")
    };

    /**
     * Gets the addon which matches the given tag
     * @param tag Tag string of the addon's RemotePackage
     * @return RemotePackage of the addon
     */
    @Nullable
    public Addon getAddon(String tag) {
        for (Addon a:addons)
            if (a.tag.equals(tag))
                return a;
        return null;
    }

    /**
     * Gets the installation state of a given addon
     * @param addon Addon to check
     * @return AddonState of the addon
     */
    public AddonState getAddonState(Addon addon) {
        if (addon == null) return AddonState.NOT_INSTALLED;
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(
                    addon.packageName, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return AddonState.NOT_INSTALLED;
        }
        if (!packageInfo.versionName.equals(addon.latestVersion)) return AddonState.INSTALLED_HAS_UPDATE;
        if (addon.isService) {
            AccessibilityManager am = (AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            for (AccessibilityServiceInfo enabledService : enabledServices) {
                ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
                if (enabledServiceInfo.packageName.equals(addon.packageName))
                    return AddonState.INSTALLED_SERVICE_ACTIVE;
            }
            return AddonState.INSTALLED_SERVICE_INACTIVE;
        } else return AddonState.INSTALLED_APP;
    }
    public void uninstallAddon(Activity activity, String tag) {
        RemotePackage addon = getAddon(tag);
        if (addon == null) return;
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        intent.setData(Uri.parse("package:" + addon.packageName));
        activity.startActivity(intent);
    }
    public void installAddon(String tag) {
        RemotePackage addon = getAddon(tag);
        if (addon == null) return; // Failsafe
        Log.v(TAG, "Attempting to install addon "+tag);
        downloadPackage(addon);
    }

}
