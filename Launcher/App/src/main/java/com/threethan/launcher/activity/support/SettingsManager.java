package com.threethan.launcher.activity.support;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.helper.QuestGameTuner;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.metadata.MetaMetadata;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.Platform;

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
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    public static final String META_LABEL_SUFFIX = ":META";

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
    private static DataStoreEditor dataStoreEditorPerApp = null;
    private final WeakReference<LauncherActivity> myLauncherActivityRef;
    private static ConcurrentHashMap<String, Set<String>> groupAppsMap = new ConcurrentHashMap<>();
    private static Set<String> appGroupsSet = Collections.synchronizedSet(new HashSet<>());
    private Set<String> selectedGroupsSet = Collections.synchronizedSet(new HashSet<>());
    private static final Map<Context, SettingsManager> instanceByContext = Collections.synchronizedMap(new HashMap<>());
    private SettingsManager(LauncherActivity activity) {
        myLauncherActivityRef = new WeakReference<>(activity);
        dataStoreEditor = activity.dataStoreEditor;
        dataStoreEditorSort = SyncCoordinator.getSortDataStore(activity);
        dataStoreEditorPerApp = SyncCoordinator.getPerAppDataStore(activity);

        if (forcedBannerApps.isEmpty())
            forcedBannerApps.addAll(dataStoreEditor.getStringSet(KEY_FORCED_BANNER, Set.of(QuestGameTuner.PKG_NAME)));

        if (forcedSquareApps.isEmpty())
            forcedSquareApps.addAll(dataStoreEditor.getStringSet(KEY_FORCED_SQUARE, Set.of()));

        // Conditional defaults (hacky)
        Settings.DEFAULT_DETAILS_LONG_PRESS = Platform.isTv();
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

    public static final HashMap<String, String> appLabelCache = new HashMap<>();
    public static final HashMap<ApplicationInfo, String> sortableLabelCache = new HashMap<>();
    static {
        appLabelCache.put(null, "");
        sortableLabelCache.put(null, "");
    }

    /**
     * Gets the label for the given app.
     * Returns the package name if it hasn't been cached yet, but then gets it asynchronously.
     */
    public static String getAppLabel(ApplicationInfo app) {
        if (app == null) return "";
        if (appLabelCache.containsKey(app.packageName)) return appLabelCache.get(app.packageName);
        final String customLabel = dataStoreEditorPerApp.getString(app.packageName, "");
        if (customLabel.isEmpty()) fetchLabelAsync(app, l -> {}, () -> {});
        String res = processAppLabel(app, customLabel);
        appLabelCache.put(app.packageName, res);
        return res;
    }
    /**
     * Gets the label for the given app.
     * @param app App to get the label for
     * @param onLabel Called when the label is ready, may be called more than once!
     */
    public static void getAppLabel(ApplicationInfo app, Consumer<String> onLabel) {
        if (appLabelCache.containsKey(app.packageName)) onLabel.accept(appLabelCache.get(app.packageName));
        final String customLabel = dataStoreEditorPerApp.getString(app.packageName, "");
        onLabel.accept(processAppLabel(app, customLabel));
        if (customLabel.isEmpty()) fetchLabelAsync(app, onLabel, () -> {});
    }

    /**
     * Asynchronously fetches the app label from metadata repo
     * @param app ApplicationInfo of the app
     * @param onLabel Called on success with the label
     * @param onError Called on error (not called if label not found)
     */
    private static void fetchLabelAsync(ApplicationInfo app, Consumer<String> onLabel, Runnable onError) {
        if (Platform.getLabelOverrides(Core.context()).containsKey(app.packageName)) return;
        if (!Platform.isVr()) return;
        executorService.submit(() -> {
            MetaMetadata.App appMeta = MetaMetadata.getForPackage(app.packageName);
            if (appMeta != null) {
                String label = appMeta.label();
                appLabelCache.put(app.packageName, label);
                dataStoreEditorPerApp.putString(app.packageName+META_LABEL_SUFFIX, label);
                onLabel.accept(label);
            } else onError.run();
        });
    }


    private final static ExecutorService executorService = Core.EXECUTOR;
    /**
     * Gets the string which should be used to sort the given app.
     * Possibly async.
     */
    @SuppressLint("CheckResult")
    public static void getSortableAppLabelsAsync(List<ApplicationInfo> apps,
                                                 BiConsumer<ApplicationInfo, String> onLabelAsync) {

        apps.forEach(app -> executorService.submit(() -> {
            if (sortableLabelCache.containsKey(app))
                onLabelAsync.accept(app, sortableLabelCache.get(app));
            else {
                String label = formatSortableAppLabel(app);
                sortableLabelCache.put(app, label);
                onLabelAsync.accept(app, label);
            }
        }));
    }
    private static String formatSortableAppLabel(ApplicationInfo app) {
        final String base = getAppLabel(app);
        final boolean isStarred = StringLib.hasPreChar(base, StringLib.STAR);
        final boolean isNew = isNewlyAddedPackage(app.packageName);

        if (!isStarred && !isNew) {
            return base;
        }

        final char[] rv = new char[base.length() + 1];
        rv[0] = isStarred ? '\1' : '\2'; // Starred first, then new
        base.getChars(0, base.length(), rv, 1);
        return new String(rv);
    }
    public static String getSortableGroupLabel(String base) {
        if (!StringLib.hasPreChar(base, StringLib.STAR)) return base;
        final char[] rv = new char[base.length() + 1];
        rv[0] = '\0';
        base.getChars(0, base.length(), rv, 1);
        return new String(rv);
    }

    private static @Nullable String processAppLabel(ApplicationInfo app, String name) {
        if (!name.isEmpty()) return name;

        Context context = Core.context();
        if (Platform.getLabelOverrides(context).containsKey(app.packageName))
            return Platform.getLabelOverrides(context).get(app.packageName);

        String metaLabel = dataStoreEditorPerApp.getString(app.packageName+META_LABEL_SUFFIX, "");
        if (!metaLabel.isEmpty()) return metaLabel;

        if (app.packageName.startsWith("http") &&
                (App.isWebsite(app.packageName) || StringLib.isSearchUrl(app.packageName))) {
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
            PackageManager pm = Core.context().getPackageManager();
            String label = app.loadLabel(pm).toString();
            if (!label.isEmpty()) return label;
            // Try to load this app's real app info
            label = (String) app.loadLabel(pm);
            if (!label.isEmpty()) return label;
        } catch (NullPointerException ignored) {}
        return "?";
    }

    public static void setAppLabel(ApplicationInfo app, String newName) {
        if (newName == null) return;
        appLabelCache.put(app.packageName, newName);
        sortableLabelCache.remove(app);
        dataStoreEditorPerApp.putString(app.packageName, newName);
        if (LauncherActivity.getForegroundInstance() != null)
            LauncherActivity.getForegroundInstance().launcherService
                    .forEachActivity(li -> li.notifyAppChanged(app, true));
    }

    public static boolean getAppLaunchOut(String pkg) {
        if (!Platform.isQuest()) return false;
        if (App.isWebsite(pkg)) {
            // If website, select based on browser selection
            final String launchBrowserKey = Settings.KEY_LAUNCH_BROWSER + pkg;
            final int launchBrowserSelection = dataStoreEditorPerApp.getInt(
                    launchBrowserKey,
                    SettingsManager.getDefaultBrowser()
            );
            return launchBrowserSelection != 0;
        }
        return App.getType(pkg) != App.Type.PANEL;
    }
    public static int getAppLaunchSize(String pkg) {
        if (pkg.equals("com.android.settings")) return 0;
        int val = dataStoreEditorPerApp.getInt(
                Settings.KEY_LAUNCH_SIZE + pkg, 0);
        if (val <= 0 && pkg.equals("com.android.documentsui")) return 1;
        return val;
    }
    public static int getDefaultBrowser() {
        return dataStoreEditor.getInt(Settings.KEY_DEFAULT_BROWSER, Platform.isQuest() ? 2 : Platform.isTv() ? 0 : 1);
    }

    public static ConcurrentHashMap<String, Set<String>> getGroupAppsMap() {
        if (groupAppsMap.isEmpty()) readGroupsAndSort();
        return groupAppsMap;
    }

    public void setAppGroup(String packageName, String group) {
        ConcurrentHashMap<String, Set<String>> gam = SettingsManager.getGroupAppsMap();
        // Remove from old group(s)
        for (String from : gam.keySet())
            Objects.requireNonNull(gam.get(from)).remove(packageName);
        // Add to new group
        Objects.requireNonNull(SettingsManager.getGroupAppsMap().get(group))
                .add(packageName);

        storeValues();
    }
    public static void setGroupAppsMap(Map<String, Set<String>> value) {
        groupAppsMap = new ConcurrentHashMap<>(value);
        writeGroupsAndSort();
    }

    /**
     * Gets the visible apps to show in the app grid
     * @param selectedGroups If true, only return apps in currently selected groups
     * @param allApps A collection of all apps
     * @return Apps which should be shown
     */
    public List<ApplicationInfo>
    getVisibleApps(List<String> selectedGroups, Collection<ApplicationInfo> allApps) {
        // Get list of installed apps
        Map<String, Set<String>> gam = getGroupAppsMap();

        if (allApps == null) {
            Log.w("Lightning Launcher", "Got null app list");
            return new ArrayList<>();
        }

        ArrayList<ApplicationInfo> unsorted = new ArrayList<>(allApps);
        unsorted.removeIf(Objects::isNull);
        gam.values().forEach(groupApps
                -> unsorted.removeIf(ai -> groupApps.contains(ai.packageName)));

        // Sort unsorted apps if needed
        for (ApplicationInfo app : unsorted) {
            App.Type type = App.getType(app);
            String targetGroup = type == App.Type.UNSUPPORTED
                    ? Settings.UNSUPPORTED_GROUP
                    : SettingsManager.getDefaultGroupFor(AppExt.getType(app));

            SettingsManager.registerNewlyAddedApp(app);

            // Create group if needed
            if (!gam.containsKey(targetGroup))
                gam.put(targetGroup, new HashSet<>());

            Set<String> group = gam.get(targetGroup);
            if (group != null)
                group.add(app.packageName);
        }

        setGroupAppsMap(gam);

        Set<String> currentPackages = new HashSet<>();
        for (String group : selectedGroups)
            if (gam.containsKey(group))
                currentPackages.addAll(Objects.requireNonNull(gam.get(group)));

        Map<String, ApplicationInfo> appByPackageName = allApps.stream().collect(Collectors.toMap(ai -> ai.packageName, x -> x));
        List<ApplicationInfo> currentApps = currentPackages.stream().map(appByPackageName::get).collect(Collectors.toList());

        // Remove disabled/uninstalled apps
        currentApps.removeIf(Objects::isNull);
        currentApps.removeIf(app -> Platform.excludedPackageNames.contains(app.packageName));

        return currentApps;
    }

    private static @Nullable Set<String> newlyAddedAppsInternalCache = null;
    /** Get apps which should show the "NEW" label */
    private static void getNewlyAddedApps() {
        // Don't flag everything right after install
        long baseline = dataStoreEditorPerApp.getLong(Settings.KEY_NEWLY_ADDED_BASELINE, -1);
        if (baseline == -1) {
            baseline = System.currentTimeMillis() + 1000 * 60; // 1-minute exclusion period
            dataStoreEditorPerApp.putLong(Settings.KEY_NEWLY_ADDED_BASELINE, baseline);
        }
        // Get newly added apps and remove them if not-so-new anymore
        Set<String> newlyAddedApps = dataStoreEditorPerApp.getStringSet(Settings.KEY_NEWLY_ADDED, new HashSet<>());
        long newLabelDurationMs
                = dataStoreEditorPerApp.getInt(Settings.KEY_TAG_MAX_DURATION,
                Settings.DEFAULT_KEY_TAG_MAX_DURATION) * 60 * 1000L * 2 / (1L + newlyAddedApps.size());

        if (!newlyAddedApps.isEmpty()) {
            long addedTimeThresh = System.currentTimeMillis() - newLabelDurationMs / newlyAddedApps.size();
            newlyAddedApps.removeIf(pkgName
                    -> dataStoreEditorPerApp.getLong(Settings.PREF_NEWLY_ADDED_TIME+pkgName, 0)
                    < addedTimeThresh);
        }

        newlyAddedAppsInternalCache = newlyAddedApps;
    }

    /** Check if a package should display the "NEW" label */
    private static boolean isNewlyAddedPackage(String packageName) {
        if (newlyAddedAppsInternalCache == null
                || newlyAddedAppsInternalCache.contains(packageName)) {
             getNewlyAddedApps();
             return newlyAddedAppsInternalCache.contains(packageName);
        }
        else return false;
    }

    /** Register a newly added app */
    private static void registerNewlyAddedApp(ApplicationInfo app) {
        if (newlyAddedAppsInternalCache == null) getNewlyAddedApps();
        newlyAddedAppsInternalCache.add(app.packageName);
        dataStoreEditorPerApp.putStringSet(Settings.KEY_NEWLY_ADDED, newlyAddedAppsInternalCache);
        dataStoreEditorPerApp.putLong(Settings.PREF_NEWLY_ADDED_TIME+app.packageName, System.currentTimeMillis());
    }

    /** Register an app as recently launched */
    public static void registerRecentlyLaunchedApp(ApplicationInfo app) {
        dataStoreEditorPerApp.putLong(Settings.PREF_LAST_LAUNCHED_TIME +app.packageName, System.currentTimeMillis());
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
        for (App.Type type : PlatformExt.getSupportedAppTypes())
            defaultGroupsSet.add(SettingsManager.defaultFallbackGroupFor(type));

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
        if (myLauncherActivityRef.get() != null
                && (LauncherActivity.groupsEnabled
                || myLauncherActivityRef.get().isEditing())) {

            // Deselect hidden
            if (!myLauncherActivityRef.get().isEditing() && !myLauncherActivityRef.get().isSearching())
                selectedGroupsSet.removeIf(s -> s.equals(Settings.HIDDEN_GROUP)
                        || groupAppsMap.get(s) == null || Objects.requireNonNull(groupAppsMap.get(s)).isEmpty());
        } else {
            selectedGroupsSet.addAll(getAppGroups());
            if (!myLauncherActivityRef.get().isSearching())
                selectedGroupsSet.remove(Settings.HIDDEN_GROUP);
        }
        return selectedGroupsSet;
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

        sortedGroupList.sort(Comparator.comparing(SettingsManager::getSortableGroupLabel));

        // Move hidden group to end
        if (sortedGroupList.contains(Settings.HIDDEN_GROUP)) {
            sortedGroupList.remove(Settings.HIDDEN_GROUP);
            sortedGroupList.add(Settings.HIDDEN_GROUP);
        }

        sortedGroupList.remove(Settings.UNSUPPORTED_GROUP);

        if (myLauncherActivityRef.get() != null && !myLauncherActivityRef.get().isEditing()
        && !myLauncherActivityRef.get().isSearching()) {
            sortedGroupList.removeIf(s -> {
                Set<String> gam = groupAppsMap.get(s);
                return s.equals(Settings.HIDDEN_GROUP ) || (gam == null || gam.isEmpty());
            });
        }

        return sortedGroupList;
    }

    /**
     * Resets all groups and sorting
     */
    public void resetGroupsAndSort(){
        dataStoreEditorSort.asyncWrite = false;
        for (String group : appGroupsSet)
            dataStoreEditorSort.removeStringSet(KEY_GROUP_APP_LIST + group);
        appGroupsSet.clear();
        groupAppsMap.clear();
        dataStoreEditorSort.removeStringSet(KEY_GROUPS);
        dataStoreEditor.removeStringSet(KEY_SELECTED_GROUPS);
        for (String group : getAppGroups())
            dataStoreEditorSort.removeStringSet(group);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        readGroupsAndSort();
        writeGroupsAndSort();

        Log.i("Groups (SettingsManager)", "Groups have been reset");
    }

    /**
     * Reads groups and app sorting from the datastore,
     * make sure not to call directly after writingGroupsAndSort or there will be issues
     * since writing is async
     */
    private static synchronized void readGroupsAndSort() {
        if (!appGroupsSet.isEmpty() && !groupAppsMap.isEmpty()) return;
        try {
            appGroupsSet.clear();
            appGroupsSet.addAll(dataStoreEditorSort.getStringSet(KEY_GROUPS, getDefaultGroupsSet()));

            groupAppsMap.clear();

            appGroupsSet.add(Settings.HIDDEN_GROUP);
            appGroupsSet.add(Settings.UNSUPPORTED_GROUP);

            for (String group : appGroupsSet) {
                Set<String> appListSet = new HashSet<>();
                appListSet = dataStoreEditorSort.getStringSet(KEY_GROUP_APP_LIST + group, appListSet);
                groupAppsMap.put(group, appListSet);
            }

        } catch (Exception e) {
            Log.e("Settings Manager", "Error while reading groups & sort", e);
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

            for (String group : appGroupsSet) {
                editor.putStringSet(KEY_GROUP_APP_LIST + group, groupAppsMap.get(group));
            }
        } catch (Exception e) {
            Log.e("Settings Manager", "Error while writing groups & sort", e);
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
               existingGroups.contains(StringLib.setPreChar(newGroupName, StringLib.STAR, false)) ||
               existingGroups.contains(StringLib.setPreChar(newGroupName, StringLib.STAR, true))
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
     * @return If the group was newly selected
     */
    public boolean selectGroup(String name) {
        boolean newlySelected = !selectedGroupsSet.contains(name);
        Set<String> selectFirst = new HashSet<>();
        selectFirst.add(name);
        setSelectedGroups(selectFirst);
        return newlySelected;
    }

    final private static Map<App.Type, String> defaultGroupCache = new ConcurrentHashMap<>();
    /** Clear the cache of default groups by app type */
    public static void clearDefaultGroupsCache() {
        defaultGroupCache.clear();
        readGroupsAndSort();
    }

    /**
     * Sets the default group for new apps of a given type
     * @param type Type of apps
     * @param newDefault Name of new default group
     */
    public static void setDefaultGroupFor(App.Type type, String newDefault) {
        if (newDefault == null) return;
        defaultGroupCache.put(type, newDefault);
        Compat.getDataStore().putString(Settings.KEY_DEFAULT_GROUP + type, newDefault);
    }

    /**
     * Gets the current default group for apps of a given type
     * @param type Type of apps
     * @return Name of default group
     */
    public static String getDefaultGroupFor(App.Type type) {
        if (defaultGroupCache.containsKey(type)) return defaultGroupCache.get(type);
        String def = checkDefaultGroupFor(type);
        if (appGroupsSet.contains(def)) defaultGroupCache.put(type, def);

        return def;
    }
    private static String checkDefaultGroupFor(App.Type type) {
        String key = Settings.KEY_DEFAULT_GROUP + type;
        String def = defaultFallbackGroupFor(type);

        String group = SettingsManager.dataStoreEditor.getString(key, def);
        Set<String> normalGroupsSet = new HashSet<>(appGroupsSet);
        normalGroupsSet.remove(Settings.HIDDEN_GROUP);
        normalGroupsSet.remove(Settings.UNSUPPORTED_GROUP);
        if (!normalGroupsSet.contains(group)) {
            if (normalGroupsSet.isEmpty()) group = def;
            else group = normalGroupsSet.iterator().next();
        }
        return group;
    }
    private static String defaultFallbackGroupFor(App.Type type) {
        return Settings.getDefaultGroupForType(Core.context(), type);
    }

    private static final Map<App.Type, Boolean> isBannerCache = new ConcurrentHashMap<>();
    /**
     * Check if a certain app type should be displayed as a banner
     * @param type Type of app
     * @return True if that type is set to display as banners
     */
    public static boolean isTypeBanner(App.Type type) {
        if (type == App.Type.TV && !Platform.isTv()) type = App.Type.PHONE;
        if (isBannerCache.containsKey(type)) return Boolean.TRUE.equals(isBannerCache.get(type));
        String key = Settings.KEY_BANNER + type;
        if (!Settings.FALLBACK_BANNER.containsKey(type)) type = App.Type.PHONE;
        boolean def = Boolean.TRUE.equals(Settings.FALLBACK_BANNER.get(type));
        boolean val = SettingsManager.dataStoreEditor.getBoolean(key, def);
        isBannerCache.put(type, val);
        return val;
    }

    /** Signal a change in the types which should be displayed as banners */
    public static void setTypeBanner(App.Type type, boolean banner) {
        LauncherActivity la = LauncherActivity.getForegroundInstance();
        if (la != null) {
            isBannerCache.put(type, banner);
            dataStoreEditor.putBoolean(Settings.KEY_BANNER + type, banner);

            la.dataStoreEditor.removeStringSet(Settings.KEY_FORCED_SQUARE);
            la.dataStoreEditor.removeStringSet(Settings.KEY_FORCED_BANNER);
            Compat.clearIconCache(la);
            la.launcherService.forEachActivity(LauncherActivity::notifyAdapterDisplayModeChanged);
        }
    }

    private static final Set<String> forcedBannerApps = new HashSet<>();
    private static final Set<String> forcedSquareApps = new HashSet<>();
    /** Sets a specific app to use banner or icon display, regardless of type.
     * notifyAdapterDisplayModeChanged MUST be called afterwards! */
    public static void
    setAppBannerOverride(ApplicationInfo app, boolean isBanner) {
        if (isBanner) {
            forcedBannerApps.add(app.packageName);
            forcedSquareApps.remove(app.packageName);
        } else {
            forcedBannerApps.remove(app.packageName);
            forcedSquareApps.add(app.packageName);
        }
        dataStoreEditor.putStringSet(KEY_FORCED_SQUARE, forcedSquareApps);
        dataStoreEditor.putStringSet(KEY_FORCED_BANNER, forcedBannerApps);
    }
    /** Call getAppOverridesBanner first! @return True, if the app overrides & is a banner */
    public static boolean getAppIsBanner(ApplicationInfo app) {
        if (app == null) return false;
        if (SettingsManager.isTypeBanner(AppExt.getType(app))) {
            return !forcedSquareApps.contains(app.packageName);
        } else {
            return forcedBannerApps.contains(app.packageName);
        }
    }

    public static void getAppLastLaunchedTimeAsync(String packageName, Consumer<Long> onTime) {
        dataStoreEditorPerApp.getLong(Settings.PREF_LAST_LAUNCHED_TIME +packageName, 0, onTime);
    }
    public static void getAppAddedTimeAsync(String packageName, Consumer<Long> onTime) {
        dataStoreEditorPerApp.getLong(Settings.PREF_NEWLY_ADDED_TIME+packageName, 0, onTime);
    }
}