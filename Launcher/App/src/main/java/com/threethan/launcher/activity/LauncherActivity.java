package com.threethan.launcher.activity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.BuildConfig;
import com.threethan.launcher.LauncherService;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.adapter.CustomItemAnimator;
import com.threethan.launcher.activity.adapter.GroupsAdapter;
import com.threethan.launcher.activity.adapter.LauncherAppsAdapter;
import com.threethan.launcher.activity.adapter.LauncherGridLayoutManager;
import com.threethan.launcher.activity.dialog.AppDetailsDialog;
import com.threethan.launcher.activity.dialog.SettingsDialog;
import com.threethan.launcher.activity.support.SortHandler;
import com.threethan.launcher.activity.support.WallpaperLoader;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.activity.view.SortCycler;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launcher.activity.view.status.StatusAdaptableView;
import com.threethan.launchercore.util.LcDialog;
import com.threethan.launchercore.view.LcBlurCanvas;
import com.threethan.launcher.activity.view.MarginDecoration;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.updater.LauncherUpdater;
import com.threethan.launcher.updater.RemotePackageUpdater;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.ImageLib;
import com.threethan.launchercore.util.Keyboard;
import com.threethan.launchercore.util.Launch;
import com.threethan.launchercore.util.Platform;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

/**
    The class handles most of what the launcher does, though it is extended by it's child classes

    It relies on LauncherService to provide it with the main view/layout of the launcher, but all
    actual usage of that view (mainView) and its children is done here.

    It contains functions for initializing, refreshing, and updating various parts of the interface.
 */

public class LauncherActivity extends Launch.LaunchingActivity {
    public static Boolean darkMode = null;
    public static Boolean groupsEnabled = true;
    private static Boolean groupsWide = false;
    private static boolean reduceMotion = Settings.DEFAULT_REDUCE_MOTION;
    public static boolean needsForceRefresh = false;
    public boolean queueOpenSettings = false;
    protected RecyclerView appsRecycler;
    protected RecyclerView groupsRecycler;
    protected View groupsBlurView;
    protected ViewGroup groupsContainerWideWindow;
    protected ViewGroup groupsContainerWideWindowCentered;
    protected ViewGroup groupsContainerNarrowWindow;
    private DataStoreEditor mDataStoreEditor;

    public DataStoreEditor getDataStoreEditor() {
        if (mDataStoreEditor == null) {
            if (Core.context() != null) mDataStoreEditor = Compat.getDataStoreEditor();
            else mDataStoreEditor = SyncCoordinator.getDefaultDataStoreEditor(this);
        }
        return mDataStoreEditor;
    }
    public View mainView;
    public View topBar;
    private int prevViewWidth;
    public boolean needsUpdateCleanup = false;
    // Settings
    public SettingsManager settingsManager;
    public boolean settingsVisible;
    public LauncherService launcherService;
    private WallpaperLoader wallpaperLoader;
    protected static final String TAG = "Lightning Launcher";
    private MarginDecoration marginDecoration;
    public static int iconMargin = -1;
    public static int iconScale = -1;

    public static boolean namesSquare;
    public static boolean namesBanner;
    public static boolean timesBanner;

    private static WeakReference<LauncherActivity> foregroundInstance = null;

    public static Locale implicitLocale = null;
    public boolean isActive = false;

    public LauncherActivity () {
        super();
    }
    /**
     * Gets an instance of a LauncherActivity, preferring that which was most recently resumed
     * @return A reference to some launcher activity
     */
    public static @Nullable LauncherActivity getForegroundInstance() {
        if (foregroundInstance == null) return null;
        return foregroundInstance.get();
    }

    /** Notify necessary components that an app has changed (eg. label update) */
    public void notifyAppChanged(ApplicationInfo app, boolean maybeEffectsStandardSort) {
        LauncherAppsAdapter adapter = getAppAdapter();
        if (adapter != null) {
            if ((maybeEffectsStandardSort && getCurrentSortMode().equals(SortHandler.SortMode.STANDARD))
                    || getCurrentSortMode().equals(SortHandler.SortMode.RECENTLY_USED))
                adapter.setActivity(this);
            adapter.notifyItemChanged(app);
        }
    }

    @Override
    public Resources getResources() {
        try {
            implicitLocale = Locale.getDefault();
            // If system language != english:
            if (!implicitLocale.getLanguage().startsWith(Settings.UNTRANSLATED_LANGUAGE)) {
                Configuration config = super.getResources().getConfiguration();
                Locale currentLocale = config.getLocales().get(0);

                // Check if user wants to force english
                boolean forceUntranslated
                        = getDataStoreEditor().getBoolean(Settings.KEY_FORCE_UNTRANSLATED, false);
                Locale targetLocale = forceUntranslated ? Locale.ENGLISH : implicitLocale;

                // Override as necessary
                if (targetLocale != currentLocale) {
                    config.setLocale(targetLocale);
                    super.getResources().updateConfiguration(config, super.getResources().getDisplayMetrics());
                }
            }
        } catch (Exception ignored) {}
        return super.getResources();
    }

    private Insets barInsets = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                        .detectAll()
                        .penaltyLog()
                        .build());
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_container);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), (v, insets) -> {
            barInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            updateInsets();
            return insets;
        });
        Core.init(this);

        if (mDataStoreEditor == null) mDataStoreEditor = getDataStoreEditor();

        Intent intent = new Intent(this, LauncherService.class);
        bindService(intent, launcherServiceConnection, Context.BIND_AUTO_CREATE);

        wallpaperLoader = new WallpaperLoader(this);
        int background = getDataStoreEditor().getInt(Settings.KEY_BACKGROUND,
        Platform.isTv()
            ? Settings.DEFAULT_BACKGROUND_TV
            : Settings.DEFAULT_BACKGROUND_VR);
        boolean custom = background < 0 || background >= SettingsManager.BACKGROUND_COLORS.length;
        int backgroundColor = custom ? Color.parseColor("#404044") : SettingsManager.BACKGROUND_COLORS[background];

        Drawable cd = new ColorDrawable(backgroundColor);
        if (Platform.isQuest()) cd.setAlpha(WallpaperLoader.getBackgroundAlpha(getDataStoreEditor()));
        post(() -> {
            getWindow().setBackgroundDrawable(cd);
            LcBlurCanvas.invalidateAll();
        });

        // Set back action
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
    }
    protected void handleBackPressed() {
        if (!settingsVisible) new SettingsDialog(this).show();
    }
    public View rootView;

    private void onBound() {

        if (hasBound) return;
        hasBound = true;
        final boolean hasView = launcherService.checkForExistingView();

        if (hasView) startWithExistingView();
        else startWithNewView();

    }
    protected void startWithNewView() {
        Log.v(TAG, "Starting with new view");
        ViewGroup containerView = findViewById(R.id.container);
        launcherService.getNewView(this, containerView, view -> {
            rootView = view;
            init();
            Compat.checkCompatibilityUpdate(this);

            refreshBackground();
            refreshAppList();
            refreshInterface();

            appsRecycler.setAlpha(0f);
            appsRecycler.post(() -> appsRecycler.animate().alpha(1f).setDuration(400).start());
        });
    }
    protected void startWithExistingView() {
        Log.v(TAG, "Starting with existing view");
        ViewGroup containerView = findViewById(R.id.container);
        rootView = launcherService.getExistingView(this, containerView);

        try {
            init();

            appsRecycler.setAlpha(1f); // Just in case the app was closed before it faded in

            // Take ownership of adapters (which are currently referencing a dead activity)
            Objects.requireNonNull(getAppAdapter()).setLauncherActivity(this);
            Objects.requireNonNull(getGroupAdapter()).setLauncherActivity(this);

            post(this::updateToolBars); // Fix visual bugs with the blur views
        } catch (Exception e) {
            Log.e(TAG, "Crashed due to exception while re-initiating existing activity", e);
        }

        refreshBackground();
    }
    protected void init() {
        Core.init(this);
        settingsManager = SettingsManager.getInstance(this);

        mainView = rootView.findViewById(R.id.mainLayout);
        topBar = mainView.findViewById(R.id.topBarLayout);

        mainView.addOnLayoutChangeListener(this::onLayoutChanged);
        appsRecycler = rootView.findViewById(R.id.apps);
        appsRecycler.setItemAnimator(reduceMotion ? null : new CustomItemAnimator());
        appsRecycler.setNestedScrollingEnabled(false);
        appsRecycler.setItemViewCacheSize(128);

        //noinspection InvalidSetHasFixedSize
        appsRecycler.setHasFixedSize(true);

        groupsRecycler = rootView.findViewById(R.id.groupsRecycler);

        groupsBlurView = rootView.findViewById(R.id.blurViewGroups);
        groupsContainerWideWindow = rootView.findViewById(R.id.groupsContainerWideWindow);
        groupsContainerWideWindowCentered = rootView.findViewById(R.id.groupsContainerWideWindowCentered);
        groupsContainerNarrowWindow = rootView.findViewById(R.id.groupsContainerNarrowWindow);

        // Set logo button
        rootView.findViewById(R.id.blurViewSettingsIcon).setOnClickListener(view -> {
            if (!settingsVisible) new SettingsDialog(this).show();
        });

        // Sort modes
        SortCycler sortCycler = rootView.findViewById(R.id.sortCycler);
        sortCycler.cycleTo(getCurrentSortMode());

        sortCycler.setOnCycledListener(mode -> {
            int newIndex = mode.ordinal();
            getDataStoreEditor().putInt(Settings.KEY_SORT, newIndex);
            if (getAppAdapter() != null) {
                getAppAdapter().setSortMode(mode);
                String displayText = mode.getDisplayText(this);
                LcDialog.toast(displayText);
            }
        });

        rootView.findViewById(R.id.blurViewSortIcon).setOnClickListener(v -> sortCycler.cycleNext());

        if (barInsets != null) updateInsets();

        View topGradient = rootView.findViewById(R.id.topGradient);

        appsRecycler.setOnScrollChangeListener((view, x, y, oldX, oldY)
                -> topGradient.setAlpha(Math.clamp((y - 50f) / 100f, 0f, 1f)));
    }

    private void updateInsets() {
        try {
            if (barInsets == null) return;
            if (rootView != null) {
                rootView.setPadding(
                        barInsets.left, 0, barInsets.right, barInsets.bottom
                );
                if (appsRecycler != null)
                    appsRecycler.setVerticalFadingEdgeEnabled(barInsets.top > 1);
                if (topBar != null) topBar.setPadding(
                        0, barInsets.top - dp(barInsets.top > 15 ? 10 : 0), 0, 0
                );
                View searchBarContainer = rootView.findViewById(R.id.searchBarContainer);
                if (searchBarContainer != null) searchBarContainer.setPadding(
                        0, barInsets.top - dp(barInsets.top > 15 ? 10 : 0), 0, 0
                );
            }
        } catch (Exception ignored) {}
    }

    protected void onLayoutChanged(View ignoredV, int ignoredLeft, int ignoredTop, int right, int bottom,
                                   int ignoredOldLeft, int ignoredOldTop, int oldRight, int oldBottom) {
        if (Math.abs(oldBottom-bottom) > 10 || Math.abs(oldRight-right) > 10) { // Only on significant diff
            wallpaperLoader.crop();
            updateGridLayouts(false);
            updateToolBars();
            postDelayed(this::updateToolBars, 1000);
            while (appsRecycler.getItemDecorationCount() > 1)
                appsRecycler.removeItemDecorationAt(appsRecycler.getItemDecorationCount()-1);
        }
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "Activity is being destroyed - "
                + (isFinishing() ? "Finishing" : "Not Finishing"));
        if (launcherService != null && isFinishing()) {
            LcDialog.closeAll();
            launcherService.destroyed(this);
        }

        if (isFinishing()) try {
            unbindService(launcherServiceConnection); // Should rarely cause exception
            // For the GC & easier debugging
            settingsManager = null;
        } catch (RuntimeException ignored) {} //Runtime exception called when a service is invalid

        isActive = false;

        super.onDestroy();
    }

    public enum FilePickerTarget {ICON, WALLPAPER, APK}
    private FilePickerTarget filePickerTarget;
    /** Opens a file picker for the given target
     * @return True if it opened successfully */
    public boolean showFilePicker(FilePickerTarget target) {
        filePickerTarget = target;

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (target.equals(FilePickerTarget.APK))
            intent.setType("application/vnd.android.package-archive");
        else
            intent.setType("image/*");

        try {
            filePicker.launch(intent);
            return true;
        } catch (Exception ignored) {
            LcDialog.toast("No image picker available!");
            return false;
        }
    }

    private ImageView selectedImageView;
    public void setSelectedIconImage(ImageView imageView) {
        selectedImageView = imageView;
    }

    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {
                        if (o.getData() != null)
                            pickFile(o.getData().getData());
                    });

    private void pickFile(Uri uri) {
        if (filePickerTarget.equals(FilePickerTarget.APK)) {
            new RemotePackageUpdater(this).installApk(uri);
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = ImageLib.bitmapFromStream(getContentResolver().openInputStream(uri));
        } catch (FileNotFoundException e) {
            Log.e("PhotoPicker", "Error on load", e);
            LcDialog.toast("Could not load selected image");
            return;
        }
        if (bitmap == null) return;
        switch (filePickerTarget) {
            case ICON -> AppDetailsDialog.onImageSelected(
                    bitmap, selectedImageView, this);
            case WALLPAPER -> {
                bitmap = ImageLib.getResizedBitmap(bitmap, 720);
                ImageLib.saveBitmap(bitmap,
                        new File(getApplicationInfo().dataDir, Settings.CUSTOM_BACKGROUND_PATH));
                bitmap.recycle();
                refreshBackground();
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        Core.init(this);
        isActive = true;

        foregroundInstance = new WeakReference<>(this);
        checkRefreshPackages();

        try {
            LauncherAppsAdapter.animateClose(this);
            // Hide KB
            Keyboard.hide( mainView);
        } catch (Exception ignored) {} // Will fail if service hasn't started yet

        postDelayed(() -> new LauncherUpdater(this).checkAppUpdateInteractive(), 1000);
    }

    static final ExecutorService refreshPackagesService = Core.EXECUTOR;
    /**
     * Reloads and refreshes the current list of packages,
     * and then the resulting app list for every activity.
     */
    public void checkRefreshPackages() {
        if (PlatformExt.installedApps == null || needsForceRefresh) {
            Log.d(TAG, "Force-Refresh Package List");
            forceRefreshPackages();
            return;
        }
        Log.d(TAG, "Checking Package List");

        refreshPackagesService.execute(() -> {
            List<ApplicationInfo> newApps = Platform.listInstalledApps();

            if (newApps.size() != PlatformExt.installedApps.size())
                runOnUiThread(() -> this.refreshPackagesInternal(newApps));
            else {

                Set<Integer> installedSet = new HashSet<>();
                PlatformExt.installedApps.forEach(v -> installedSet.add(v.packageName.hashCode()));
                Set<Integer> newSet = new HashSet<>();
                newApps.forEach(v -> newSet.add(v.packageName.hashCode()));

                if (newSet.size() != installedSet.size() || !installedSet.containsAll(newSet)) {
                    Log.d(TAG, "Refresh Package List due to mismatch");
                    runOnUiThread(() -> this.refreshPackagesInternal(newApps));
                }
            }
        });
    }

    private static int forceRefreshCounter = 0;
    private static boolean forceRefreshOnCooldown = false;
    public void forceRefreshPackages() {
        Log.v(TAG, "Package Refresh - Forced");

        needsForceRefresh = false;

        if (PlatformExt.installedApps == null || PlatformExt.installedApps.isEmpty()) {
            // Synchronous initial load
            refreshPackagesInternal(Platform.listInstalledApps());
        } else if (!forceRefreshOnCooldown) {
            forceRefreshCounter++;
            final int finalForceRefreshCounter = forceRefreshCounter;
            postDelayed(() -> {
                if (forceRefreshCounter == finalForceRefreshCounter) {
                    refreshPackagesInternal(Platform.listInstalledApps());
                    forceRefreshOnCooldown = true;
                    postDelayed(() -> forceRefreshOnCooldown = false, 1000);
                }
            }, 1000);
        }
    }
    private void refreshPackagesInternal(List<ApplicationInfo> newApps) {
        PlatformExt.installedApps = newApps;

        Log.v(TAG, "Package reload - Found "+ PlatformExt.installedApps.size() +" packages");
        AppExt.invalidateCaches();

        if (launcherService != null) launcherService.forEachActivity(LauncherActivity::refreshAppList);
    }


    /**
     * Updates various properties relating to the top bar & search bar, including visibility
     * Note that these same views are also often manipulated in LauncherActivitySearchable
     */
    public void updateToolBars() {
        groupsBlurView.setVisibility(groupsEnabled.equals(Boolean.TRUE) ? View.VISIBLE : View.GONE);

        if (isEditing() && !groupsEnabled.equals(Boolean.TRUE)) setEditMode(false); // If groups were disabled while in edit mode

        LcBlurCanvas.setOverlayColor((Color.parseColor(darkMode.equals(Boolean.TRUE) ? "#20000000" : "#40FFFFFF")));

        // Item visibility
        View statusView = rootView.findViewById(R.id.blurViewStatus);
        statusView.setVisibility(getDataStoreEditor().getBoolean(Settings.KEY_SHOW_STATUS, Settings.DEFAULT_SHOW_STATUS) ? View.VISIBLE : View.GONE);
        View sortView = rootView.findViewById(R.id.blurViewSortIcon);
        sortView.setVisibility(getDataStoreEditor().getBoolean(Settings.KEY_SHOW_SORT, Settings.DEFAULT_SHOW_SORT) ? View.VISIBLE : View.GONE);
        View searchView = rootView.findViewById(R.id.blurViewSearchIcon);
        searchView.setVisibility(getDataStoreEditor().getBoolean(Settings.KEY_SHOW_SEARCH, Settings.DEFAULT_SHOW_SEARCH) ? View.VISIBLE : View.GONE);
        View settingsView = rootView.findViewById(R.id.blurViewSettingsIcon);
        settingsView.setVisibility(getDataStoreEditor().getBoolean(Settings.KEY_SHOW_SETTINGS, Settings.DEFAULT_SHOW_SETTINGS) ? View.VISIBLE : View.GONE);

        View searchBar = rootView.findViewById(R.id.blurViewSearchBar);

        View[] itemViews = new View[]{
                groupsBlurView,
                statusView,
                sortView,
                searchView,
                settingsView,
                searchBar,
        };
        for (View itemView : itemViews
        ) {
            itemView.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            itemView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            itemView.setClipToOutline(true);

            if (itemView instanceof ViewGroup itemViewGroup) {
                for (int i = 0; i < itemViewGroup.getChildCount(); i++) {
                    View child = itemViewGroup.getChildAt(i);
                    if (child instanceof StatusAdaptableView sChild)
                        sChild.setDarkMode(darkMode);
                    if (child instanceof ViewGroup childGroup) {
                        for (int j = 0; j < childGroup.getChildCount(); j++) {
                            View grandChild = childGroup.getChildAt(j);
                            if (grandChild instanceof StatusAdaptableView gsChild)
                                gsChild.setDarkMode(darkMode);
                        }
                    }
                }
            }
        }

        if (Platform.isQuest()) {
            statusView.setOnClickListener(v -> {
                ApplicationInfo quickSettingsApp = new ApplicationInfo();
                quickSettingsApp.packageName = "systemux://quick_settings";
                LaunchExt.launchApp(this, quickSettingsApp);
            });
        } else if (AppExt.packageExists("com.android.tv.settings")) {
            statusView.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.tv.settings",
                        "com.android.tv.settings.MainSettings"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        } else {
            statusView.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }

        if (sortView.getVisibility() != View.VISIBLE) {
            post(() -> {
                if (getAppAdapter() != null)
                    getAppAdapter().setSortMode(getCurrentSortMode());
            });
        }

        post(() -> { if (needsUpdateCleanup) Compat.doUpdateCleanup(this); });
    }

    /** Gets the current sort mode for the apps list */
    private SortHandler.SortMode getCurrentSortMode() {
        boolean sortVisible = getDataStoreEditor().getBoolean(Settings.KEY_SHOW_SORT, Settings.DEFAULT_SHOW_SORT);
        if (!sortVisible) return SortHandler.SortMode.STANDARD;
        int sortIndex = getDataStoreEditor().getInt(Settings.KEY_SORT, Settings.DEFAULT_SORT);
        SortHandler.SortMode[] values = SortHandler.SortMode.values();
        return values[sortIndex % values.length];
    }

    public int lastSelectedGroup;

    /**
     * Refreshes most things to do with the interface, including calling refreshAdapters();
     * It is extended further by child classes
     */
    public void refreshInterface() {
        Log.v(TAG, "Refreshing interface (incl. Adapters)");

        refreshAdapters();

        // Fix some focus issues
        final View focused = getCurrentFocus();
        if (focused != null) focused.clearFocus();
        post(() -> {
            if (focused != null && getCurrentFocus() == null) focused.requestFocus();
            if (queueOpenSettings) new SettingsDialog(this).show();
            queueOpenSettings = false;
        });
    }

    /**
     * Refreshes the display and layout of the RecyclerViews used for the groups list and app grid.
     * Includes a call to updateGridLayouts();
     */
    public void refreshAdapters() {
        prevViewWidth = -1;

        darkMode = getDataStoreEditor()
                .getBoolean(Settings.KEY_DARK_MODE, Settings.DEFAULT_DARK_MODE);
        groupsEnabled = getDataStoreEditor()
                .getBoolean(Settings.KEY_GROUPS_ENABLED, Settings.DEFAULT_GROUPS_ENABLED);
        groupsWide = getDataStoreEditor()
                .getBoolean(Settings.KEY_GROUPS_WIDE, Settings.DEFAULT_GROUPS_WIDE);

        namesSquare = getDataStoreEditor()
                .getBoolean(Settings.KEY_SHOW_NAMES_SQUARE, Settings.DEFAULT_SHOW_NAMES_SQUARE);
        namesBanner = getDataStoreEditor()
                .getBoolean(Settings.KEY_SHOW_NAMES_BANNER, Settings.DEFAULT_SHOW_NAMES_BANNER);
        timesBanner = getDataStoreEditor()
                .getBoolean(Settings.KEY_SHOW_TIMES_BANNER, Settings.DEFAULT_SHOW_TIMES_BANNER);

        setReduceMotion(getDataStoreEditor()
                .getBoolean(Settings.KEY_REDUCE_MOTION, Settings.DEFAULT_REDUCE_MOTION));
        try {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(!darkMode);
            controller.setAppearanceLightNavigationBars(!darkMode);
        } catch (Exception ignored) {}


        // Animate only once
        updateSelectedGroups();
        updateGridLayouts(false);
    }

    protected void updateSelectedGroupsAnimated() {
        updateSelectedGroups();
        if (isEditing()) return;
        if (reduceMotion) return;
        try {
            appsRecycler.setAlpha(0f);
            LcBlurCanvas.invalidateAll();
            Objects.requireNonNull(getAppAdapter()).setOnListReadyOneShot(()
                    -> appsRecycler.post(()
                    -> appsRecycler.post(() // Double post to ensure it runs after layout
                    -> appsRecycler.animate().alpha(1f).setDuration(200).start())));
        } catch (Exception ignored) {}
        // Fallback
        appsRecycler.postDelayed(() -> appsRecycler.setAlpha(1f), 500);
    }
    protected void updateSelectedGroups() {
        groupsRecycler.setAdapter(new GroupsAdapter(this, isEditing()));
        if (getAppAdapter() == null) {
            appsRecycler.setAdapter(new LauncherAppsAdapter(this));
            getAppAdapter().setOnListReadyEveryTime(() -> appsRecycler.scrollToPosition(0));
        } else {
            getAppAdapter().updateSelectedGroups();
        }
        getAppAdapter().setContainer(findViewById(R.id.appsContainer));

        // Apply sort mode
        post(() -> {
            if (getAppAdapter() != null) getAppAdapter().setSortMode(getCurrentSortMode());
        });
    }


    /**
     * Updates the heights and layouts of grid layout managers used by the groups bar and app grid
     */
    public void updateGridLayouts(boolean force) {
        int mainWidth = mainView.getWidth();
        if (mainWidth == prevViewWidth && !force) return;
        prevViewWidth = mainWidth;

        // Group rows and relevant values
        if (prevViewWidth < 1) {
            post(() -> post(() -> updateGridLayouts(true)));
            return;
        }
        final int targetWidth
                = dp(groupsWide ? Settings.GROUP_WIDTH_DP_WIDE : Settings.GROUP_WIDTH_DP);
        final int groupCols = getGroupAdapter() == null ? 1
                : Math.min(getGroupAdapter().getCount(), prevViewWidth / targetWidth);

        // Dynamic placement of groups bar
        boolean mainViewIsWideEnoughForGroups = mainView.getMeasuredWidth() > dp(groupsWide ? 1000 : 750);

        int targetWidthTotal = groupCols * (targetWidth + dp(12));
        boolean shouldUseCenteredContainer
                = !groupsWide && mainViewIsWideEnoughForGroups;
        if (shouldUseCenteredContainer) {
            int[] loc = new int[2];
            groupsContainerWideWindow.getLocationInWindow(loc);
            int leftInWindow = loc[0] - dp(6);
            int rightInWindow = mainWidth - loc[0] - groupsContainerWideWindow.getMeasuredWidth();
            int maxAllowableCenteredWidth = mainWidth - 2 * Math.max(leftInWindow, rightInWindow);
            if (targetWidthTotal > maxAllowableCenteredWidth) {
                shouldUseCenteredContainer = false;
            }
        }
        if (shouldUseCenteredContainer) {
            ViewGroup.LayoutParams lp = groupsContainerWideWindowCentered.getLayoutParams();
            lp.width = targetWidthTotal;
            groupsContainerWideWindowCentered.setLayoutParams(lp);
        }


        ViewGroup groupsViewParent = mainViewIsWideEnoughForGroups
                        ? (
                                shouldUseCenteredContainer
                                        ? groupsContainerWideWindowCentered
                                        : groupsContainerWideWindow
                                )
                        : groupsContainerNarrowWindow;
        if (groupsViewParent != groupsBlurView.getParent()) {
            ViewParent oldParent = groupsBlurView.getParent();
            if (oldParent instanceof ViewGroup) {
                ((ViewGroup) oldParent).removeView(groupsBlurView);
                groupsViewParent.addView(groupsBlurView);
            }
        }
        groupsContainerNarrowWindow
                .setVisibility(mainViewIsWideEnoughForGroups || !groupsEnabled ? View.GONE : View.VISIBLE);

        groupsRecycler.setLayoutManager(new GridLayoutManager(this, Math.max(1, groupCols)));

        // Measure the top bar to determine additional top padding for the app grid
        topBar.measure(View.MeasureSpec.makeMeasureSpec(mainView.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mainView.getMeasuredHeight(), View.MeasureSpec.AT_MOST));
        int groupHeight = topBar.getMeasuredHeight() - dp(40); // Minus margins

        if (groupHeight > mainView.getMeasuredHeight() / 3) {
            // Scroll groups if more than 1/3 the screen
            groupHeight = mainView.getMeasuredHeight() / 3;

            ViewGroup.LayoutParams lp = groupsRecycler.getLayoutParams();
            lp.height = groupHeight;
            groupsRecycler.setLayoutParams(lp);
        } else {
            ViewGroup.LayoutParams lp = groupsRecycler.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            groupsRecycler.setLayoutParams(lp);
        }
        groupsRecycler.post(() -> groupsRecycler.setVisibility(View.VISIBLE));

        updatePadding(groupHeight);

        GridLayoutManager gridLayoutManager = (GridLayoutManager) appsRecycler.getLayoutManager();
        if (gridLayoutManager == null) {
            gridLayoutManager = new LauncherGridLayoutManager(this, 3);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return Objects.requireNonNull(appsRecycler.getAdapter()).getItemViewType(position);
                }
            });
            appsRecycler.setLayoutManager(gridLayoutManager);
        }

        GridLayoutManager finalGridLayoutManager = gridLayoutManager;
        getDataStoreEditor().getInt(Settings.KEY_SCALE, Settings.DEFAULT_SCALE, scale -> {
            int estimatedWidth = prevViewWidth;

            int nCol = estimatedWidth/(dp(scale)*2) * 2; // To nearest 2
            if (nCol <= 2) nCol = 2;

            finalGridLayoutManager.setSpanCount(nCol);
        });

        topBar.setVisibility(View.VISIBLE);
    }
    /**
     * Called by updateGridLayouts, updates padding on the app grid views:
     * - Top padding to account for the groups bar
     * - Side padding to account for icon margins (otherwise icons would touch window edges)
     * - Bottom padding to account for icon margin, as well as the edit mode footer if applicable
     */
    private void updatePadding(int groupHeight) {
        if (iconMargin == -1) iconMargin = getDataStoreEditor().getInt(Settings.KEY_MARGIN, Settings.DEFAULT_MARGIN);
        if (iconScale  == -1) iconScale  = getDataStoreEditor().getInt(Settings.KEY_SCALE , Settings.DEFAULT_SCALE );

        int targetSize = dp(iconScale);
        int margin = getMargin(targetSize);

        final int topAdd = groupHeight > 1 && !isSearching() ? dp(35) + groupHeight : dp(23);
        final int bottomAdd = groupHeight > 1 ? getBottomBarHeight() + dp(11) : margin / 2 + getBottomBarHeight() + dp(Platform.isVr() ? 15 : 25);

        appsRecycler.setPadding(
                dp((margin+27)*(Platform.isVr() ? 1f : 1.25f)),
                topAdd,
                dp((margin+27)*(Platform.isVr() ? 1f : 1.25f)),
                bottomAdd);

        // Margins
        if (marginDecoration == null) {
            marginDecoration = new MarginDecoration(margin);
            appsRecycler.addItemDecoration(marginDecoration);
        } else marginDecoration.setMargin(margin);
        appsRecycler.invalidateItemDecorations();
        while (appsRecycler.getItemDecorationCount() > 1)
            appsRecycler.removeItemDecorationAt(appsRecycler.getItemDecorationCount()-1);
    }

    /** Get the margin, in dp, for the app grid */
    private int getMargin(int targetSize) {
        int estimatedWidth = prevViewWidth;

        final int nCol = estimatedWidth / (targetSize * 2) * 2; // To nearest 2
        final float fCol = (float) (estimatedWidth) / (targetSize * 2) * 2;
        final float dCol = fCol - nCol;

        final float normScale = (float) (iconScale - Settings.MIN_SCALE) / (Settings.MAX_SCALE - Settings.MIN_SCALE);
        int margin = (int) ((iconMargin) * (normScale+0.5f)/1.5f);
        margin += (int) ((dCol-0.5) * 75 / nCol);
        margin -= 20;
        if (margin < -20) margin = -20;
        return margin;
    }

    /**
     * Accounts for the height of the edit mode footer when visible.
     * Actual function in child class, as the base LauncherActivity is not editable.
     * @return Height of the bottom bar in px
     */
    protected int getBottomBarHeight() {
        return 0;
    }

    /**
     * Sets a background color to the window, nav-bar & status-bar  based on your chosen background,
     * then calls an additional Executor to actually load the background image
     */
    public void refreshBackground() {
        ExecutorService executorService = Core.EXECUTOR;
        executorService.execute(() -> {
            // Set initial color, execute background task
            if (backgroundIndex == -2) {
                backgroundIndex = getDataStoreEditor().getInt(Settings.KEY_BACKGROUND,
                        Platform.isTv()
                                ? Settings.DEFAULT_BACKGROUND_TV
                                : Settings.DEFAULT_BACKGROUND_VR);
            }

            boolean custom = backgroundIndex < 0 || backgroundIndex >= SettingsManager.BACKGROUND_COLORS.length;
            int backgroundColor = custom ? Color.parseColor("#404044") : SettingsManager.BACKGROUND_COLORS[backgroundIndex];
            Drawable cd = new ColorDrawable(backgroundColor);

            if (Platform.isQuest()) cd.setAlpha(WallpaperLoader.getBackgroundAlpha(getDataStoreEditor()));

            if (Platform.isQuest()) cd.setAlpha(200);

            wallpaperLoader.load();
        });
    }
    public static int backgroundIndex = -2; // -2 indicated the setting needs to be loaded

    /**
     * Sets the background to the given index, automatically setting dark mode on/off if applicable
     * @param index of the new background
     */
    public void setBackground(int index) {
        if (index >= SettingsManager.BACKGROUND_DRAWABLES.length || index < 0) index = -1;
        else darkMode = SettingsManager.BACKGROUND_DARK[index];
        getDataStoreEditor().putInt(Settings.KEY_BACKGROUND, index);
        LauncherActivity.backgroundIndex = index;
        launcherService.forEachActivity(LauncherActivity::refreshBackground);
    }

    /**
     * Used to update the actual content of app list used to the main app grid
     */
    public void refreshAppList() {
        if (PlatformExt.installedApps == null) {
            Log.v(TAG, "Package list empty, forcing package refresh");
            checkRefreshPackages();
            return;
        }

        refreshAdapters();

        if (getAppAdapter() != null) {
            getAppAdapter().setLauncherActivity(this);
            getAppAdapter().setCompleteAppSet(PlatformExt.listInstalledApps(this));
        }
    }

    /**
     * Perform the action for clicking a group
     * @param position Index of the group
     * @param source Source of the click (optional)
     */
    public void clickGroup(int position, View source) {
        checkRefreshPackages();
        lastSelectedGroup = position;
        // This method is replaced with a greatly expanded one in the child class
        final List<String> groupsSorted = settingsManager.getAppGroupsSorted(false);
        final String group = groupsSorted.get(position);
        boolean doAnimation = settingsManager.selectGroup(group);

        if (doAnimation) {
            updateSelectedGroupsAnimated();
        } else {
            updateSelectedGroups();
        }
    }
    /**
     * Perform the action for long clicking a group
     * @param position Index of the group
     * @return True if this is the only selected group and should therefor show a menu
     */
    public boolean longClickGroup(int position) {
        lastSelectedGroup = position;

        List<String> groups = settingsManager.getAppGroupsSorted(false);
        Set<String> selectedGroups = settingsManager.getSelectedGroups();

        if (position >= groups.size() || position < 0) return false;

        String item = groups.get(position);
        if (selectedGroups.contains(item)) selectedGroups.remove(item);
        else selectedGroups.add(item);
        if (selectedGroups.isEmpty()) {
            selectedGroups.add(item);
            return true;
        }
        settingsManager.setSelectedGroups(selectedGroups);
        refreshAdapters();
        return false;
    }

    // Utility functions
    public void post(Runnable action) {
        if (mainView == null) action.run();
        else mainView.post(action);
    }
    public void postDelayed(Runnable action, int ms) {
        if (mainView == null) new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                action.run();
            }
        }, ms);
        else mainView.postDelayed(action, ms);
    }

    /**
     * Converts a value from display pixels to pixels
     * @param dip display pixel value
     * @return Pixel value
     */
    public int dp(float dip) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
    }

    @Nullable
    public LauncherAppsAdapter getAppAdapter() {
        if (appsRecycler == null) return null;
        return (LauncherAppsAdapter) appsRecycler.getAdapter();
    }
    @Nullable
    public GroupsAdapter getGroupAdapter() {
        if (groupsRecycler == null) return null;
        return (GroupsAdapter) groupsRecycler.getAdapter();
    }

    /**
     * Gets a set of the packageName of every package
     * @return Set of packageNames
     */
    public Set<String> getAllPackages() {
        Set<String> packages = new HashSet<>();
        PlatformExt.listInstalledApps(this).forEach(a -> packages.add(a.packageName));
        return packages;
    }

    // Services
    private boolean hasBound = false;

    /** Defines callbacks for service binding, passed to bindService(). */
    private final ServiceConnection launcherServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            LauncherService.LocalBinder binder = (LauncherService.LocalBinder) service;
            launcherService = binder.getService();
            onBound();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {}
    };

    /**
     * Calls a full update of group and app adapters (slow!)
     */
    @SuppressLint("NotifyDataSetChanged")
    public void resetAdapters() {
        SettingsManager.sortableLabelCache.clear();
        if (getAppAdapter() != null) {
            refreshAppList();
            getAppAdapter().notifyAllChanged();
        }
        if (getGroupAdapter() != null) getGroupAdapter().notifyDataSetChanged();
        refreshInterface();
    }

    /** Must be called if the display mode (icon/banner) is changed */
    public void notifyAdapterDisplayModeChanged() {
        SettingsManager.sortableLabelCache.clear();
        appsRecycler.setAdapter(null);
        refreshAppList();
        updateSelectedGroups();
    }

    // Edit mode stubs, to be overridden by child
    public void setEditMode(boolean b) {
        Log.w(TAG, "Tried to set edit mode on an uneditable activity");
    }
    public boolean selectApp(String packageName) {
        Log.w(TAG, "Tried to select app on an uneditable activity");
        return false;
    }
    public boolean isSelected(String packageName) {
        return false;
    }
    public boolean isEditing() { return false; }
    public boolean canEdit() { return false; }
    public void addWebsite() {}
    /** @noinspection BooleanMethodIsAlwaysInverted*/
    public boolean isSearching() { return false; }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) ActivityCapture.takeAndStoreCapture(this);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        isActive = false;
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActive = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActive = false;
    }

    public void setReduceMotion(boolean reduceMotion) {
        if (reduceMotion != LauncherActivity.reduceMotion) {
            if (getAppAdapter() != null) getAppAdapter().clearAppFocus();
            LauncherActivity.reduceMotion = reduceMotion;
            if (reduceMotion) {
                appsRecycler.setItemAnimator(null);
            } else {
                appsRecycler.setItemAnimator(new CustomItemAnimator());
            }
        }
    }

    public static boolean shouldReduceMotion() {
        return LauncherActivity.reduceMotion;
    }
}