package com.threethan.launcher;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;

import com.threethan.launcher.platforms.AbstractPlatform;
import com.threethan.launcher.platforms.AndroidPlatform;
import com.threethan.launcher.platforms.VRPlatform;
import com.threethan.launcher.ui.AppsAdapter;
import com.threethan.launcher.ui.GroupsAdapter;
import com.esafirm.imagepicker.features.ImagePicker;
import com.esafirm.imagepicker.model.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;
class DeferredTask extends AsyncTask {
    private Activity owner;
    @Override
    protected Object doInBackground(Object[] objects) {
        owner = (Activity) objects[0];

        BlurView blurView0 = owner.findViewById(R.id.blurView0);
        BlurView blurView1 = owner.findViewById(R.id.blurView1);

        float blurRadiusDp = 20f;

        View windowDecorView = owner.getWindow().getDecorView();
        ViewGroup rootViewGroup = windowDecorView.findViewById(android.R.id.content);

        Drawable windowBackground = windowDecorView.getBackground();

        blurView0.setupWith(rootViewGroup, new RenderScriptBlur(owner.getApplicationContext())) // or RenderEffectBlur
                .setFrameClearDrawable(windowBackground) // Optional
                .setBlurRadius(blurRadiusDp);
        blurView1.setupWith(rootViewGroup, new RenderScriptBlur(owner.getApplicationContext())) // or RenderEffectBlur
                .setFrameClearDrawable(windowBackground) // Optional
                .setBlurRadius(blurRadiusDp);

        blurView0.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurView0.setClipToOutline(true);
        blurView1.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurView1.setClipToOutline(true);

        return null;
    }
}

class BackgroundTask extends AsyncTask {

    Drawable backgroundThemeDrawable;
    MainActivity owner;
    int newScaleValueIndex;
    boolean editMode;
    boolean names;
    @Override
    protected Object doInBackground(Object[] objects) {
        owner = (MainActivity) objects[0];
        int backgroundThemeIndex = owner.sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_THEME, owner.DEFAULT_THEME);
        if (backgroundThemeIndex < owner.THEME_DRAWABLES.length) {
            backgroundThemeDrawable = owner.getDrawable(owner.THEME_DRAWABLES[backgroundThemeIndex]);
        } else {
            File file = new File(owner.getApplicationInfo().dataDir, owner.CUSTOM_THEME);
            Bitmap themeBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            backgroundThemeDrawable = new BitmapDrawable(owner.getResources(), themeBitmap);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Object _n) {
        owner.backgroundImageView.setImageDrawable(backgroundThemeDrawable);
    }
}

class ReloadTask extends AsyncTask {

    Drawable backgroundThemeDrawable;
    MainActivity owner;
    int newScaleValueIndex;
    boolean editMode;
    boolean names;
    @Override
    protected Object doInBackground(Object[] objects) {
        owner = (MainActivity) objects[0];

        names = owner.sharedPreferences.getBoolean(SettingsProvider.KEY_CUSTOM_NAMES, owner.DEFAULT_NAMES);
        newScaleValueIndex = owner.getPixelFromDip(owner.sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_SCALE, owner.DEFAULT_SCALE));
        owner.appGridView.setColumnWidth(newScaleValueIndex);

        newScaleValueIndex += owner.getPixelFromDip(8);
        editMode = owner.sharedPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false);

        return null;
    }

    @Override
    protected void onPostExecute(Object _n) {
        owner.appGridView.setAdapter(new AppsAdapter(owner, editMode, newScaleValueIndex, names));
        owner.groupPanelGridView.setAdapter(new GroupsAdapter(owner, editMode));

        owner.groupPanelGridView.setNumColumns(Math.min(owner.groupPanelGridView.getAdapter().getCount(), GroupsAdapter.MAX_GROUPS - 1));

    }
}

public class MainActivity extends Activity {
    public static final int PICK_ICON_CODE = 450;
    public static final int PICK_THEME_CODE = 95;
    public static final String CUSTOM_THEME = "theme.png";
    static final boolean DEFAULT_NAMES = true;
    private static final int DEFAULT_OPACITY = 255;
    static final int DEFAULT_SCALE = 105;
    static final int DEFAULT_THEME = 0;
    static final int[] THEME_DRAWABLES = {
            R.drawable.bg_px_purple,
            R.drawable.bg_px_red,
            R.drawable.bg_px_green,
            R.drawable.bg_px_orange,
            R.drawable.bg_px_white,
    };
    private ImageView[] selectedThemeImageViews;
    GridView appGridView;
    ImageView backgroundImageView;
    GridView groupPanelGridView;
    private boolean activityHasFocus;
    SharedPreferences sharedPreferences;
    private SettingsProvider settingsProvider;
    private ImageView selectedImageView;

    private boolean settingsPageOpen = false;
    private boolean lookPageOpen = false;
    private boolean platformsPageOpen = false;

    public static void run(Context context) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.w("LauncherStartup", "SetView");


        setContentView(R.layout.activity_main);


        Log.w("LauncherStartup", "SetViewDone");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        settingsProvider = SettingsProvider.getInstance(this);

        Log.w("LauncherStartup", "ProvidersDone");

        // Get UI instances
        RelativeLayout mainView = findViewById(R.id.linearLayoutMain);
        appGridView = findViewById(R.id.appsView);
        backgroundImageView = findViewById(R.id.background);
        groupPanelGridView = findViewById(R.id.groupsView);

        Log.w("LauncherStartup", "FindViewsDone");
        // Handle group click listener
        groupPanelGridView.setOnItemClickListener((parent, view, position, id) -> {
            List<String> groups = settingsProvider.getAppGroupsSorted(false);
            if (position == groups.size()) {
                settingsProvider.selectGroup(GroupsAdapter.HIDDEN_GROUP);
            } else if (position == groups.size() + 1) {
                settingsProvider.selectGroup(settingsProvider.addGroup());
            } else {
                settingsProvider.selectGroup(groups.get(position));
            }
            reloadUI();
        });

        // Multiple group selection
        groupPanelGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!sharedPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false)) {
                List<String> groups = settingsProvider.getAppGroupsSorted(false);
                Set<String> selectedGroups = settingsProvider.getSelectedGroups();

                String item = groups.get(position);
                if (selectedGroups.contains(item)) {
                    selectedGroups.remove(item);
                } else {
                    selectedGroups.add(item);
                }
                if (selectedGroups.isEmpty()) {
                    selectedGroups.add(groups.get(0));
                }
                settingsProvider.setSelectedGroups(selectedGroups);
                reloadUI();
            }
            return true;
        });

        // Set logo button
        ImageView settingsLogoImageView = findViewById(R.id.logo);
        settingsLogoImageView.setOnClickListener(view -> {
            if (!settingsPageOpen) {
                showSettingsMain();
                settingsPageOpen = true;
            }
        });

        new DeferredTask().execute(this);

    }

    @Override
    public void onBackPressed() {
        if (AbstractPlatform.isMagicLeapHeadset()) {
            super.onBackPressed();
        } else {
            if (!settingsPageOpen) {
                showSettingsMain();
                settingsPageOpen = true;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityHasFocus = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.w("LauncherResume", "start");
        activityHasFocus = true;

        String[] requiredPermissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        boolean hasReadPermission = checkSelfPermission(requiredPermissions[0]) == PackageManager.PERMISSION_GRANTED;
        boolean hasWritePermission = checkSelfPermission(requiredPermissions[1]) == PackageManager.PERMISSION_GRANTED;
        if (hasReadPermission && hasWritePermission) {
            reloadUI();
        } else {
            requestPermissions(requiredPermissions, 0);
        }
        Log.w("LauncherResume", "end");

    }

    public void setSelectedImageView(ImageView imageView) {
        selectedImageView = imageView;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ICON_CODE) {
            if (resultCode == RESULT_OK) {
                for (Image image : ImagePicker.getImages(data)) {
                    ((AppsAdapter) appGridView.getAdapter()).onImageSelected(image.getPath(), selectedImageView);
                    break;
                }
            } else {
                ((AppsAdapter) appGridView.getAdapter()).onImageSelected(null, selectedImageView);
            }
        } else if (requestCode == PICK_THEME_CODE) {
            if (resultCode == RESULT_OK) {

                for (Image image : ImagePicker.getImages(data)) {
                    Bitmap themeBitmap = ImageUtils.getResizedBitmap(BitmapFactory.decodeFile(image.getPath()), 1280);
                    ImageUtils.saveBitmap(themeBitmap, new File(getApplicationInfo().dataDir, CUSTOM_THEME));
                    setTheme(selectedThemeImageViews, THEME_DRAWABLES.length);
                    reloadUI();
                    break;
                }
            }
        }
    }

    public String getSelectedPackage() {
        return ((AppsAdapter) appGridView.getAdapter()).getSelectedPackage();
    }

    public void reloadUI() {

        // set customization


        new BackgroundTask().execute(this);
        new ReloadTask().execute(this);
    }

    public void setTheme(ImageView[] views, int index) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(SettingsProvider.KEY_CUSTOM_THEME, index);
        editor.apply();
        reloadUI();

        for (ImageView image : views) {
            image.setBackground(getDrawable(R.drawable.bkg_button));
            image.setImageAlpha(255);
        }
        views[index].setBackground(getDrawable(R.drawable.bkg_button_sel));
        views[index].setImageAlpha(192);
    }

    private boolean editMode = false;

    private void showSettingsMain() {
        Dialog dialog = PopupUtils.showPopup(this, R.layout.dialog_settings);

        dialog.setOnDismissListener(dialogInterface -> settingsPageOpen = false);

        ImageView apps = dialog.findViewById(R.id.settings_apps_image);
        editMode = !sharedPreferences.getBoolean(SettingsProvider.KEY_EDITMODE, false);
        apps.setImageResource(editMode ? R.drawable.ic_editing_on : R.drawable.ic_editing_off);
        dialog.findViewById(R.id.settings_apps).setOnClickListener(view1 -> {
            ArrayList<String> selectedGroups = settingsProvider.getAppGroupsSorted(true);
            if (editMode && (selectedGroups.size() > 1)) {
                Set<String> selectFirst = new HashSet<>();
                selectFirst.add(selectedGroups.get(0));
                settingsProvider.setSelectedGroups(selectFirst);
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SettingsProvider.KEY_EDITMODE, editMode);
            editor.apply();
            reloadUI();
            editMode = !editMode;
            apps.setImageResource(editMode ? R.drawable.ic_editing_on : R.drawable.ic_editing_off);
        });

        dialog.findViewById(R.id.settings_look).setOnClickListener(view -> {
            if (!lookPageOpen) {
                showSettingsLook();
                lookPageOpen = true;
            }
        });
        dialog.findViewById(R.id.settings_platforms).setOnClickListener(view -> {
            if (!platformsPageOpen) {
                showSettingsPlatforms();
                platformsPageOpen = true;
            }
        });
    }

    private void showSettingsLook() {
        Dialog dialog = PopupUtils.showPopup(this, R.layout.dialog_look);

        dialog.setOnDismissListener(dialogInterface -> lookPageOpen = false);

        Switch names = dialog.findViewById(R.id.switch_names);
        names.setChecked(sharedPreferences.getBoolean(SettingsProvider.KEY_CUSTOM_NAMES, DEFAULT_NAMES));
        names.setOnCheckedChangeListener((compoundButton, value) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SettingsProvider.KEY_CUSTOM_NAMES, value);
            editor.apply();
            reloadUI();
        });

        SeekBar opacity = dialog.findViewById(R.id.bar_opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(SettingsProvider.KEY_CUSTOM_OPACITY, value);
                editor.apply();
                reloadUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        opacity.setProgress(sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_OPACITY, DEFAULT_OPACITY));
        opacity.setMax(255);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            opacity.setMin(0);
        }

        SeekBar scale = dialog.findViewById(R.id.bar_scale);
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(SettingsProvider.KEY_CUSTOM_SCALE, value + 55);
                editor.apply();
                reloadUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        scale.setProgress(sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_SCALE, DEFAULT_SCALE) -55);
        scale.setMax(210 - 55);
        // scale.setMin(55);

        int theme = sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_THEME, DEFAULT_THEME);
        ImageView[] views = {
                dialog.findViewById(R.id.theme0),
                dialog.findViewById(R.id.theme1),
                dialog.findViewById(R.id.theme2),
                dialog.findViewById(R.id.theme3),
                dialog.findViewById(R.id.theme4),
                dialog.findViewById(R.id.theme_custom)
        };
        for (ImageView image : views) {
            image.setBackground(getDrawable(R.drawable.bkg_button));
            image.setImageAlpha(255);
        }
        views[theme].setBackground(getDrawable(R.drawable.bkg_button_sel));
        views[theme].setImageAlpha(192);
        for (int i = 0; i < views.length; i++) {
            int index = i;
            views[i].setOnClickListener(view12 -> {
                if (index >= THEME_DRAWABLES.length) {
                    selectedThemeImageViews = views;
                    ImageUtils.showImagePicker(this, PICK_THEME_CODE);
                } else {
                    setTheme(views, index);
                }
            });
        }
    }

    private void showSettingsPlatforms() {
        Dialog dialog = PopupUtils.showPopup(this, R.layout.dialog_platforms);

        dialog.setOnDismissListener(dialogInterface -> platformsPageOpen = false);

        View androidPlatformButton = dialog.findViewById(R.id.settings_android);
        final boolean[] isAndroidPlatformEnabled = {sharedPreferences.getBoolean(SettingsProvider.KEY_PLATFORM_ANDROID, true)};
        androidPlatformButton.setAlpha(isAndroidPlatformEnabled[0] ? 1.0F : 0.5F );
        androidPlatformButton.setOnClickListener(view -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            isAndroidPlatformEnabled[0] = !isAndroidPlatformEnabled[0];
            androidPlatformButton.setAlpha(isAndroidPlatformEnabled[0] ? 1.0F : 0.5F );
            editor.putBoolean(SettingsProvider.KEY_PLATFORM_ANDROID, isAndroidPlatformEnabled[0]);
            editor.apply();
            reloadUI();
        });
        androidPlatformButton.setVisibility(new AndroidPlatform().isSupported(this) ? View.VISIBLE : View.GONE);

        View vrPlatformButton = dialog.findViewById(R.id.settings_vr);
        final boolean[] isVrPlatformEnabled = {sharedPreferences.getBoolean(SettingsProvider.KEY_PLATFORM_VR, true)};
        vrPlatformButton.setAlpha(isVrPlatformEnabled[0] ? 1.0F : 0.5F );
        vrPlatformButton.setOnClickListener(view -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            isVrPlatformEnabled[0] = !isVrPlatformEnabled[0];
            vrPlatformButton.setAlpha(isVrPlatformEnabled[0] ? 1.0F : 0.5F );
            editor.putBoolean(SettingsProvider.KEY_PLATFORM_VR, isVrPlatformEnabled[0]);
            editor.apply();
            reloadUI();
        });
        vrPlatformButton.setVisibility(new VRPlatform().isSupported(this) ? View.VISIBLE : View.GONE);
    }

    int getPixelFromDip(int dip) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
    }

    public void openApp(ApplicationInfo app) {
        //fallback action
        new Thread(() -> {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (activityHasFocus && !AbstractPlatform.isHTCHeadset()) {
                openAppDetails(app.packageName);
            }
        }).start();

        //open the app
        AbstractPlatform platform = AbstractPlatform.getPlatform(app);
        platform.runApp(this, app);
    }

    public void openAppDetails(String pkg) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + pkg));
        startActivity(intent);
    }
}