package com.threethan.launcher.support;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.AppData;
import com.threethan.launcher.helper.DataStoreEditor;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.helper.Settings;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.lib.StringLib;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An instance of this class is tied to each launcher activity, and it is used to get and store
 * most (but not all) preferences. It handles the conversion of data between types that are usable
 * and types which can be stored to shared preferences.
 * <p>
 * It handles customizable properties (label, launch mode) as well grouping.
 * <p>
 * It also provides a number of static methods which are used by various other classes .
 */
public class SettingsManager extends Settings {

    /**
     * Return a list of versions where wallpapers were reordered in some way, for Compat
     */
    public static List<Integer> getVersionsWithBackgroundChanges() {
        List<Integer> out = new ArrayList<>();
        out.add(1);
        out.add(2);
        return out;
    }

    //storage
    private static DataStoreEditor dataStoreEditor = null;
    private static DataStoreEditor dataStoreEditorSort = null;
    private final WeakReference<LauncherActivity> myLauncherActivityRef;
    private static ConcurrentHashMap<String, String> appGroupMap = new ConcurrentHashMap<>();
    private static Set<String> appGroupsSet = Collections.synchronizedSet(new HashSet<>());
    private Set<String> selectedGroupsSet = Collections.synchronizedSet(new HashSet<>());
    private static final Map<Context, SettingsManager> instanceByContext = Collections.synchronizedMap(new HashMap<>());
    private SettingsManager(LauncherActivity activity) {
        myLauncherActivityRef = new WeakReference<>(activity);
        dataStoreEditor = activity.dataStoreEditor;
        dataStoreEditorSort = new DataStoreEditor(activity, "sort");
        // Conditional defaults (hacky)
        Settings.DEFAULT_DETAILS_LONG_PRESS = Platform.isTv(activity);
    }

    /**
     * Returns a unique instance for the given context,
     * but always the same instance for the same context
     */
    public static synchronized SettingsManager getInstance(LauncherActivity context) {
        if (instanceByContext.containsKey(context)) return SettingsManager.instanceByContext.get(context);
        instanceByContext.put(context, new SettingsManager(context));
        return instanceByContext.get(context);
    }

    public static HashMap<ApplicationInfo, String> appLabelCache = new HashMap<>();

    /**
     * Gets the label for the given app.
     * Returns the package name if it hasn't been cached yet, but then gets it asynchronously.
     */
    public static String getAppLabel(ApplicationInfo app) {
        if (appLabelCache.containsKey(app)) return appLabelCache.get(app);
        final String customLabel = dataStoreEditor.getString(app.packageName, "");
        return processAppLabel(app, customLabel);
    }

    /**
     * Gets the string which should be used to sort the given app
     */
    public static String getSortableAppLabel(ApplicationInfo app) {
        return  (App.isBanner(app) ? "0" : "1") + StringLib.forSort(getAppLabel(app));
    }

    private static @Nullable String processAppLabel(ApplicationInfo app, String name) {
        if (!name.isEmpty()) return name;

        if (AppData.labelOverrides.containsKey(app.packageName))
            return AppData.labelOverrides.get(app.packageName);
        if (App.isWebsite(app) || StringLib.isSearchUrl(app.packageName)) {
            try {
                name = app.packageName.split("//")[1];
                String[] split = name.split("\\.");
                if (split.length <= 1) name = app.packageName;
                else if (split.length == 2) name = split[0];
                else name = split[1];

                if (StringLib.isSearchUrl(app.packageName))
                    name += " Search";

                if (!name.isEmpty()) return StringLib.toTitleCase(name);
            } catch (Exception ignored) {
            }
        }
        try {
            PackageManager pm = LauncherActivity.getAnyInstance().getPackageManager();
            String label = app.loadLabel(pm).toString();
            if (!label.isEmpty()) return label;
            // Try to load this app's real app info
            label = (String) app.loadLabel(pm);
            if (!label.isEmpty()) return label;
        } catch (NullPointerException ignored) {}
        return null;
    }

    public static void setAppLabel(ApplicationInfo app, String newName) {
        if (newName == null) return;
        appLabelCache.put(app, newName);
        dataStoreEditor.putString(app.packageName, newName);
        LauncherActivity.getAnyInstance().launcherService.forEachActivity(LauncherActivity::refreshAppList);
    }
    public static boolean getAppLaunchOut(String pkg) {
        if (App.isWebsite(pkg)) {
            // If website, select based on browser selection
            final String launchBrowserKey = Settings.KEY_LAUNCH_BROWSER + pkg;
            final int launchBrowserSelection = LauncherActivity.getAnyInstance().dataStoreEditor.getInt(
                    launchBrowserKey,
                    SettingsManager.getDefaultBrowser()
            );
            final int stringRes = Settings.launchBrowserStrings[launchBrowserSelection];
            return (stringRes == R.string.browser_default_out || stringRes == R.string.browser_quest);
        }
        // Else use saved setting
        return dataStoreEditor.getBoolean(Settings.KEY_LAUNCH_OUT_PREFIX+pkg,
                dataStoreEditor.getBoolean(Settings.KEY_DEFAULT_LAUNCH_OUT, DEFAULT_DEFAULT_LAUNCH_OUT));
    }
    public static void setAppLaunchOut(String pkg, boolean shouldLaunchOut) {
        dataStoreEditor.putBoolean(Settings.KEY_LAUNCH_OUT_PREFIX+pkg, shouldLaunchOut);
    }
    public static int getDefaultBrowser() {
        return dataStoreEditor.getInt(Settings.KEY_DEFAULT_BROWSER, 0);
    }
    public static ConcurrentHashMap<String, String> getAppGroupMap() {
        if (appGroupMap.isEmpty()) readGroupsAndSort();
        return appGroupMap;
    }
    public void setAppGroup(String packageName, String group) {
        getAppGroupMap();
        appGroupMap.put(packageName, group);
        storeValues();
    }
    public static void setAppGroupMap(Map<String, String> value) {
        appGroupMap = new ConcurrentHashMap<>(value);
        writeGroupsAndSort();
    }

    /**
     * Gets the visible apps to show in the app grid
     * @param selectedGroups If true, only return apps in currently selected groups
     * @param allApps A collection of all apps
     * @return Apps which should be shown
     */
    public List<ApplicationInfo> getVisibleApps(List<String> selectedGroups, Collection<ApplicationInfo> allApps) {
        // Get list of installed apps
        ConcurrentHashMap<String, String> apps = getAppGroupMap();

        if (allApps == null) {
            Log.w("Lightning Launcher", "Got null app list");
//            launcherActivity.post(launcherActivity::reloadPackages);
            return new ArrayList<>();
        }

        // Sort into groups
        for (ApplicationInfo app : new ArrayList<>(allApps)) {
            if (!App.isSupported(app))
                apps.put(app.packageName, Settings.UNSUPPORTED_GROUP);
            else if (!apps.containsKey(app.packageName) ||
                    Objects.equals(apps.get(app.packageName), Settings.UNSUPPORTED_GROUP)){
                apps.put(app.packageName, App.getDefaultGroupFor(App.getType(app)));
            }
        }

        // Save changes to app list
        setAppGroupMap(apps);

        List<ApplicationInfo> currentApps = new ArrayList<>(allApps);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("OLD API", "Your android version is too old so things won't work right.");
            for (ApplicationInfo app : Collections.unmodifiableCollection(allApps))
                if (!(apps.containsKey(app.packageName) && selectedGroups.contains(apps.get(app.packageName))))
                    currentApps.remove(app);
            return currentApps;
        }
        currentApps.removeIf(app -> !(apps.containsKey(app.packageName) && selectedGroups.contains(apps.get(app.packageName))));

        // Must be set here, else labels might async load during sort which causes crashes
        Map<ApplicationInfo, String> labels = new HashMap<>();
        for (ApplicationInfo app : currentApps)
            labels.put(app, SettingsManager.getSortableAppLabel(app));

        currentApps.sort(Comparator.comparing(labels::get));
        // Sort Done!
        return currentApps;
    }

    /**
     * Gets the set of all groups
     * @return Set containing all app groups
     */
    public static Set<String> getAppGroups() {
        if (appGroupsSet.isEmpty()) readGroupsAndSort();
        return appGroupsSet;
    }

    /**
     * Sets the set of all groups
     * @param appGroups The new set of app groups
     */
    public void setAppGroups(Set<String> appGroups) {
        appGroupsSet = Collections.synchronizedSet(appGroups);
        storeValues();
    }

    /**
     * Gets the set of groups which should be set when there are no groups
     * or when things are reset to default.
     * @return The default set of groups
     */
    public static Set<String> getDefaultGroupsSet() {
        Set<String> defaultGroupsSet = new HashSet<>();
        for (App.Type type : Platform.getSupportedAppTypes(LauncherActivity.getAnyInstance()))
            defaultGroupsSet.add(App.getDefaultGroupFor(type));

        return (defaultGroupsSet);
    }

    /**
     * Gets the set of currently selected groups, in no particular order
     * @return Set of selected groups
     */
    public Set<String> getSelectedGroups() {
        if (selectedGroupsSet.isEmpty()) {
            selectedGroupsSet.addAll(dataStoreEditor.getStringSet(KEY_SELECTED_GROUPS, getDefaultGroupsSet()));
        }
        if (myLauncherActivityRef.get() != null &&
                LauncherActivity.groupsEnabled || myLauncherActivityRef.get().isEditing()) {

            // Deselect hidden
            if (myLauncherActivityRef.get() != null && !myLauncherActivityRef.get().isEditing()
                    && dataStoreEditor.getBoolean(Settings.KEY_AUTO_HIDE_EMPTY, Settings.DEFAULT_AUTO_HIDE_EMPTY)) {
                for (Object group : selectedGroupsSet.toArray()) {
                    if (!appGroupMap.containsValue((String) group))
                        selectedGroupsSet.remove((String) group);
                }
            }
            return selectedGroupsSet;
        } else {
            Set<String> retSet = new HashSet<>(appGroupsSet);
            retSet.remove(Settings.HIDDEN_GROUP);
            return retSet;
        }
    }

    /**
     * Sets the current selection of groups, pass an empty set to clear
     * @param appGroups New set of groups to be selected
     */
    public void setSelectedGroups(Set<String> appGroups) {
        selectedGroupsSet = Collections.synchronizedSet(appGroups);
        storeValues();
    }

    /**
     * Gets the list of groups, in order
     * @param selected If true, only selected groups are returned
     * @return The list of groups
     */
    public ArrayList<String> getAppGroupsSorted(boolean selected) {
        if ((selected ? selectedGroupsSet : appGroupsSet).isEmpty()) readGroupsAndSort();
        ArrayList<String> sortedGroupList = new ArrayList<>(selected ? getSelectedGroups() : getAppGroups());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            sortedGroupList.sort(Comparator.comparing(StringLib::forSort));
        else
            Log.e("OLD API", "Your android version is too old (<7.0) " +
                    "so groups can not be sorted," +
                    "which may cause serious issues!");

        // Move hidden group to end
        if (sortedGroupList.contains(Settings.HIDDEN_GROUP)) {
            sortedGroupList.remove(Settings.HIDDEN_GROUP);
            sortedGroupList.add(Settings.HIDDEN_GROUP);
        }

        sortedGroupList.remove(Settings.UNSUPPORTED_GROUP);

        if (myLauncherActivityRef.get() != null && !myLauncherActivityRef.get().isEditing()
                && dataStoreEditor.getBoolean(Settings.KEY_AUTO_HIDE_EMPTY, Settings.DEFAULT_AUTO_HIDE_EMPTY)) {
            for (Object group: sortedGroupList.toArray()) {
                if (!appGroupMap.containsValue((String) group)) sortedGroupList.remove((String) group);
            }
        }

        return sortedGroupList;
    }

    /**
     * Resets all groups and sorting
     */
    public void resetGroupsAndSort(){
        for (String group : appGroupsSet)
            dataStoreEditorSort.removeStringSet(KEY_GROUP_APP_LIST + group);
        appGroupsSet.clear();
        appGroupMap.clear();
        dataStoreEditorSort.removeStringSet(KEY_GROUPS);
        dataStoreEditor.removeStringSet(KEY_SELECTED_GROUPS);
        for (String group : getAppGroups())
            dataStoreEditorSort.removeStringSet(group);

        readGroupsAndSort();
        writeGroupsAndSort();

        Log.i("Groups (SettingsManager)", "Groups have been reset");
    }

    /**
     * Reads groups and app sorting from the datastore,
     * make sure not to call directly after writingGroupsAndSort or there will be issues
     * since writing is async
     */
    private static synchronized void readGroupsAndSort
    () {
        try {
            appGroupsSet.clear();
            appGroupsSet.addAll(dataStoreEditorSort.getStringSet(KEY_GROUPS, getDefaultGroupsSet()));

            appGroupMap.clear();

            appGroupsSet.add(Settings.HIDDEN_GROUP);
            appGroupsSet.add(Settings.UNSUPPORTED_GROUP);
            for (String group : appGroupsSet) {
                Set<String> appListSet = new HashSet<>();
                appListSet = dataStoreEditorSort.getStringSet(KEY_GROUP_APP_LIST + group, appListSet);
                for (String app : appListSet) appGroupMap.put(app, group);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    synchronized private void storeValues() {
        dataStoreEditor.putStringSet(KEY_SELECTED_GROUPS, selectedGroupsSet);
        writeGroupsAndSort();
    }

    /**
     * Writes the current sorting of apps and set of groups to the dataStore
     */
    public synchronized static void writeGroupsAndSort() {
        try {
            DataStoreEditor editor = dataStoreEditorSort;
            editor.putStringSet(KEY_GROUPS, appGroupsSet);

            Map<String, Set<String>> appListSetMap = new HashMap<>();
            for (String group : appGroupsSet) appListSetMap.put(group, new HashSet<>());
            for (String pkg : appGroupMap.keySet()) {
                Set<String> group = appListSetMap.get(appGroupMap.get(pkg));
                if (group == null) group = appListSetMap.get(
                        App.getDefaultGroupFor(App.Type.TYPE_SUPPORTED));
                if (group == null) {
                    Log.w("Group was null", pkg);
                    group = appListSetMap.get(HIDDEN_GROUP);
                    appGroupMap.put(pkg, HIDDEN_GROUP);
                }
                assert group != null;
                group.add(pkg);
            }
            for (String group : appGroupsSet) {
                editor.putStringSet(KEY_GROUP_APP_LIST + group, appListSetMap.get(group));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a new group, which is named automatically
     * @return Name of the new group
     */
    public String addGroup() {
        String newGroupName = "New";
        List<String> existingGroups = getAppGroupsSorted(false);
        if (
               existingGroups.contains(StringLib.setStarred(newGroupName, false)) ||
               existingGroups.contains(StringLib.setStarred(newGroupName, true))
        ) {
            int index = 2;
            while (existingGroups.contains(newGroupName + " " + index)) {
                index++;
            }
            newGroupName = newGroupName + " " + index;
        }
        existingGroups.add(newGroupName);
        setAppGroups(new HashSet<>(existingGroups));
        return newGroupName;
    }

    /**
     * Select a group, adding it to the list of selected groups
     * @param name Name of the group to select
     */
    public void selectGroup(String name) {
        Set<String> selectFirst = new HashSet<>();
        selectFirst.add(name);
        setSelectedGroups(selectFirst);
    }

    final private static Map<App.Type, String> defaultGroupCache = new ConcurrentHashMap<>();

    /**
     * Sets the default group for new apps of a given type
     * @param type Type of apps
     * @param newDefault Name of new default group
     */
    public static void setDefaultGroupFor(App.Type type, String newDefault) {
        if (newDefault == null) return;
        defaultGroupCache.put(type, newDefault);
        LauncherActivity.getAnyInstance().dataStoreEditor.putString(Settings.KEY_DEFAULT_GROUP + type, newDefault);
    }

    /**
     * Gets the current default group for apps of a given type
     * @param type Type of apps
     * @return Name of default group
     */
    public static String getDefaultGroupFor(App.Type type) {
        if (defaultGroupCache.containsKey(type)) return defaultGroupCache.get(type);
        String def = checkDefaultGroupFor(type);
        defaultGroupCache.put(type, def);
        return def;
    }
    private static String checkDefaultGroupFor(App.Type type) {
        String key = Settings.KEY_DEFAULT_GROUP + type;
        if (!Settings.FALLBACK_GROUPS.containsKey(type)) type = App.Type.TYPE_PHONE;
        String def = Settings.FALLBACK_GROUPS.get(type);

        return SettingsManager.dataStoreEditor.getString(key, def);
    }

    private static final Map<App.Type, Boolean> isBannerCache = new ConcurrentHashMap<>();
    /**
     * Check if a certain app type should be displayed as a banner
     * @param type Type of app
     * @return True if that type is set to display as banners
     */
    public static boolean isTypeBanner(App.Type type) {
        if (isBannerCache.containsKey(type)) return Boolean.TRUE.equals(isBannerCache.get(type));
        String key = Settings.KEY_BANNER + type;
        if (!Settings.FALLBACK_BANNER.containsKey(type)) type = App.Type.TYPE_PHONE;
        boolean def = Boolean.TRUE.equals(Settings.FALLBACK_BANNER.get(type));
        boolean val = SettingsManager.dataStoreEditor.getBoolean(key, def);
        isBannerCache.put(type, val);
        return val;
    }

    /** Signal a change in the types which should be displayed as banners */
    public static void setTypeBanner(App.Type type, boolean banner) {
        isBannerCache.put(type, banner);
        dataStoreEditor.putBoolean(Settings.KEY_BANNER + type, banner);
    }

    /**
     * Check if advanced chain-loader size options should be show
     * @return True if advanced size options are currently enabled
     */
    public static boolean getShowAdvancedSizeOptions(LauncherActivity activity) {
        return activity.dataStoreEditor.getBoolean(Settings.KEY_ADVANCED_SIZING,
                Settings.DEFAULT_ADVANCED_SIZING);
    }
 }