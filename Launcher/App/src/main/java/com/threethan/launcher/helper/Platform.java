package com.threethan.launcher.helper;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;

import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.lib.StringLib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *     This abstract class stores lists of apps.
 *     It also provides a few helper functions for adding websites, and decides if we're in VR
 */
public abstract class Platform {
    public static List<ApplicationInfo> installedApps;
    public static Set<ApplicationInfo> apps = Collections.synchronizedSet(new HashSet<>());
    public static int changeIndex = 0; //Used to track changes, specifically adding websites

    /**
     * Finds an existing website on the launcher
     * @return Null if not found, else name of group website is in
     */
    public static String findWebsite(DataStoreEditor dataStoreEditor, String url) {
        url = StringLib.fixUrl(url);

        Set<String> webApps = dataStoreEditor.getStringSet(Settings.KEY_WEBSITE_LIST, Collections.emptySet());
        if (!webApps.contains(url)) return null;
        else return SettingsManager.getAppGroupMap().get(url);
    }

    /**
     * Add a website to the launcher
     * @param dataStoreEditor DataStoreEditor instance to use
     * @param url Url to launch
     */
    public static void addWebsite(DataStoreEditor dataStoreEditor, String url) {
        addWebsite(dataStoreEditor, url, null);
    }

    /**
     * Add a website to the launcher
     * @param dataStoreEditor DataStoreEditor instance to use
     * @param url Url to launch
     * @param name Name for the website on the launcher
     */
    public static String addWebsite(DataStoreEditor dataStoreEditor, String url, String name) {
        url = StringLib.fixUrl(url);

        Set<String> webApps = dataStoreEditor.getStringSet(Settings.KEY_WEBSITE_LIST, Collections.emptySet());
        webApps = new HashSet<>(webApps); // Copy since we're not supposed to modify directly
        webApps.add(url);

        dataStoreEditor.putStringSet(Settings.KEY_WEBSITE_LIST, webApps);
        if (name != null) dataStoreEditor.putString(url, name);

        changeIndex ++;
        return url;
    }

    protected static Boolean isTv;
    protected static Boolean isQuest;
    public static boolean isVr(Activity activity) {
        // Quest reports itself as UI_MODE_NORMAL
        return !isTv(activity);
    }
    /** Returns true if running on a Meta Quest device */
    public static boolean isQuest(Activity activity) {
        if (isQuest != null) return isQuest;
        isQuest = App.doesPackageExist(activity, "com.oculus.vrshell");
        return isQuest;
    }

    public static final String BROWSER_PACKAGE = "com.threethan.browser";
    /** Returns true if Lightning Browser is installed */
    public static boolean hasBrowser(Activity activity) {
        return App.doesPackageExist(activity, BROWSER_PACKAGE);
    }
    /** Returns true if running on a TV */
    public static boolean isTv(Activity activity) {
        if (isTv != null) return isTv;
        UiModeManager uiModeManager = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);
        isTv = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        return isTv;
    }
    /** Returns true if we're on a tv and have already
     * checked using the other version of this function */
    public static boolean isTv() {
        return isTv;
    }

    // Get a list of valid app types depending on platform
    private static List<App.Type> cachedSupportedAppTypes;

    /**
     * Returns a list of app types which are supported.
     * <p>
     * The Quest ignored TV apps, while TVs ignore VR apps.
     * <p>
     * Note that "unsupported" apps may end up being supported, but put into a different category.
     *
     * @return Types of apps which are supported on this device
     */
    public static List<App.Type> getSupportedAppTypes(Activity activity) {
        if (cachedSupportedAppTypes != null) return cachedSupportedAppTypes;

        final List<App.Type> validTypes = new ArrayList<>();


        // These must be in order of priority
        if (Platform.isQuest(activity)) validTypes.add(App.Type.TYPE_PANEL);

        if (Platform.isTv(activity)) validTypes.add(App.Type.TYPE_TV);
        if (Platform.isVr(activity)) validTypes.add(App.Type.TYPE_VR);

        validTypes.add(App.Type.TYPE_WEB);
        validTypes.add(App.Type.TYPE_PHONE);

        cachedSupportedAppTypes = validTypes;
        return validTypes;
    }
}