package com.threethan.launcher.activity.dialog;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.helper.SettingsSaver;
import com.threethan.launcher.updater.LauncherUpdater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/*
    SettingsDialog

    This class handles the main settings page, including setting/getting preferences, button states,
    and making sure settings are correctly applied to launcher activities
 */

public class SettingsDialog extends BasicDialog<LauncherActivity> {
    private static boolean clearedLabel;
    private static boolean clearedIconCache;
    private static boolean clearedIconCustom;
    private static boolean clearedSort;
    private static boolean clearedGroups;

    /**
     * Creates a settings dialog. Make sure to call show()!
     * @param launcherActivity Parent LauncherActivity instance
     */
    public SettingsDialog(LauncherActivity launcherActivity) {
        super(launcherActivity, R.layout.dialog_settings);
    }

    public AlertDialog show() {
        AlertDialog dialog = super.show();
        if (dialog == null) return null;

        context.settingsVisible = true;

        dialog.setOnDismissListener(dialogInterface -> context.settingsVisible = false);
        View dismiss = dialog.findViewById(R.id.dismissButton);
        dismiss.setOnClickListener(view -> dialog.dismiss());
        dismiss.post(dismiss::requestFocus);

        // Addons
        View addonsButton = dialog.findViewById(R.id.addonsButton);
        addonsButton.setOnClickListener(view -> new AddonDialog(context).show());
        if (!context.dataStoreEditor.getBoolean(Settings.KEY_SEEN_ADDONS, false)) {
            View addonsButtonAttract = dialog.findViewById(R.id.addonsButtonAttract);
            addonsButtonAttract.setVisibility(View.VISIBLE);
            addonsButton.setVisibility(View.GONE);
            addonsButtonAttract.setOnClickListener(view -> {
                context.dataStoreEditor.putBoolean(Settings.KEY_SEEN_ADDONS, true);
                new AddonDialog(context).show();
            });
        }

        // Edit
        Switch editSwitch = dialog.findViewById(R.id.editModeSwitch);
        editSwitch.setOnClickListener(view1 -> {
            context.setEditMode(!context.isEditing());
            ArrayList<String> selectedGroups = context.settingsManager.getAppGroupsSorted(true);
            if (context.isEditing() && (selectedGroups.size() > 1)) {
                Set<String> selectFirst = new HashSet<>();
                selectFirst.add(selectedGroups.get(0));
                context.settingsManager.setSelectedGroups(selectFirst);
            }
        });

        View addWebsite = dialog.findViewById(R.id.addWebsiteButton);
        addWebsite.setOnClickListener(v -> context.addWebsite(context));

        // Group enabled state
        if (context.canEdit()) {
            // Can edit, show switch
            editSwitch.setChecked(context.isEditing());
            dialog.findViewById(R.id.editModeContainer).setVisibility(View.VISIBLE);
            addWebsite.setVisibility(View.GONE);
        } else {
            dialog.findViewById(R.id.editModeContainer).setVisibility(View.GONE);
            addWebsite.setVisibility(View.VISIBLE);
        }

        // Update button
        if (LauncherUpdater.isAppUpdateAvailible()) {
            View skippedUpdateButton = dialog.findViewById(R.id.updateButton);
            skippedUpdateButton.setVisibility(View.VISIBLE);
            skippedUpdateButton.setOnClickListener((view) -> new LauncherUpdater(context).checkAppUpdateAndInstall());
        }

        // Wallpaper and style
        Switch darkSwitch = dialog.findViewById(R.id.darkModeSwitch);
        attachSwitchToSetting(darkSwitch,
                Settings.KEY_DARK_MODE, Settings.DEFAULT_DARK_MODE,
                value -> {
                    LauncherActivity.darkMode = value;
                    context.launcherService.forEachActivity(LauncherActivity::resetAdapters);
                });
        ImageView[] views = {
                dialog.findViewById(R.id.background0),
                dialog.findViewById(R.id.background1),
                dialog.findViewById(R.id.background2),
                dialog.findViewById(R.id.background3),
                dialog.findViewById(R.id.background4),
                dialog.findViewById(R.id.background5),
                dialog.findViewById(R.id.background6),
                dialog.findViewById(R.id.background7),
                dialog.findViewById(R.id.background8),
                dialog.findViewById(R.id.background9),
                dialog.findViewById(R.id.background_custom)
        };
        int background = context.dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                Platform.isTv(context)
                        ? Settings.DEFAULT_BACKGROUND_TV
                        : Settings.DEFAULT_BACKGROUND_VR);
        if (background < 0) background = views.length - 1;

        for (ImageView image : views) {
            image.setClipToOutline(true);
        }
        final int wallpaperWidth = 32;
        final int selectedWallpaperWidthPx = context.dp(445 + 20 - (wallpaperWidth + 4) * (views.length - 1) - wallpaperWidth);
        views[background].getLayoutParams().width = selectedWallpaperWidthPx;
        views[background].requestLayout();
        for (int i = 0; i < views.length; i++) {
            int index = i;
            views[i].setOnClickListener(view -> {
                int lastIndex = context.dataStoreEditor.getInt(Settings.KEY_BACKGROUND,
                        Platform.isTv(context)
                                ? Settings.DEFAULT_BACKGROUND_TV
                                : Settings.DEFAULT_BACKGROUND_VR);
                if (lastIndex >= SettingsManager.BACKGROUND_DRAWABLES.length || lastIndex < 0)
                    lastIndex = SettingsManager.BACKGROUND_DRAWABLES.length;
                ImageView last = views[lastIndex];

                if (index == views.length-1) context.showImagePicker(LauncherActivity.ImagePickerTarget.WALLPAPER);
                if (last == view) return;

                ValueAnimator viewAnimator = ValueAnimator.ofInt(view.getWidth(), selectedWallpaperWidthPx);
                viewAnimator.setDuration(250);
                viewAnimator.setInterpolator(new DecelerateInterpolator());
                viewAnimator.addUpdateListener(animation -> {
                    view.getLayoutParams().width = (int) animation.getAnimatedValue();
                    view.requestLayout();
                });
                viewAnimator.start();

                ValueAnimator lastAnimator = ValueAnimator.ofInt(last.getWidth(), context.dp(wallpaperWidth));
                lastAnimator.setDuration(250);
                lastAnimator.setInterpolator(new DecelerateInterpolator());
                lastAnimator.addUpdateListener(animation -> {
                    last.getLayoutParams().width = (int) animation.getAnimatedValue();
                    last.requestLayout();
                });
                lastAnimator.start();

                // Set the background
                context.setBackground(index);

                // Set if darkSwitch mode is switch was automatically en/disabled
                if (index != views.length-1) darkSwitch.setChecked(LauncherActivity.darkMode);
            });
        }

        // Icons & Layout
        SeekBar scale = dialog.findViewById(R.id.scaleSeekBar);
        scale.setProgress(context.dataStoreEditor.getInt(Settings.KEY_SCALE, Settings.DEFAULT_SCALE));

        scale.post(() -> scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                context.dataStoreEditor.putInt(Settings.KEY_SCALE, value);
                LauncherActivity.iconScale = value;
                context.refreshInterface();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                context.launcherService.forEachActivity(LauncherActivity::refreshInterface);
            }
        }));
        scale.setMax(Settings.MAX_SCALE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) scale.setMin(Settings.MIN_SCALE);

        SeekBar margin = dialog.findViewById(R.id.marginSeekBar);
        margin.setProgress(context.dataStoreEditor.getInt(Settings.KEY_MARGIN, Settings.DEFAULT_MARGIN));
        margin.post(() -> margin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                context.dataStoreEditor.putInt(Settings.KEY_MARGIN, value);
                LauncherActivity.iconMargin = value;
                context.refreshInterface();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                context.launcherService.forEachActivity(LauncherActivity::refreshInterface);
            }
        }));
        margin.setMax(40);


        attachSwitchToSetting(dialog.findViewById(R.id.groupSwitch),
                Settings.KEY_GROUPS_ENABLED, Settings.DEFAULT_GROUPS_ENABLED,
                value -> {
                    showOneTimeWarningDialog(R.layout.dialog_info_hide_groups_tv,
                                             Settings.KEY_SEEN_HIDDEN_GROUPS_POPUP);
                    if (value) {
                        // Can edit, show switch
                        editSwitch.setChecked(context.isEditing());
                        dialog.findViewById(R.id.editModeContainer).setVisibility(View.VISIBLE);
                        addWebsite.setVisibility(View.GONE);
                    } else {
                        dialog.findViewById(R.id.editModeContainer).setVisibility(View.GONE);
                        addWebsite.setVisibility(View.VISIBLE);
                    }
                }
        );

        // Clear buttons (limited to one use to prevent bugs due to spamming)
        clearedLabel = false;

        View clearLabel = dialog.findViewById(R.id.clearLabelButton);
        clearLabel.setOnClickListener(view -> {
            if (!clearedLabel) {
                Compat.clearLabels(context);
                clearLabel.setAlpha(0.5f);
                clearedLabel = true;
            }
        });
        View iconSettings = dialog.findViewById(R.id.iconSettingsButton);
        iconSettings.setOnClickListener(view -> SettingsDialog.showIconSettings(context));

        View groupSettings = dialog.findViewById(R.id.groupDefaultsInfoButton);
        groupSettings.setOnClickListener(view -> showGroupSettings());

        // Banner mode
        final Map<App.Type, Switch> switchByType = new HashMap<>();
        switchByType.put(App.Type.TYPE_PHONE, dialog.findViewById(R.id.banner2dSwitch));
        switchByType.put(App.Type.TYPE_VR, dialog.findViewById(R.id.bannerVrSwitch));
        switchByType.put(App.Type.TYPE_TV, dialog.findViewById(R.id.bannerTvSwitch));
        switchByType.put(App.Type.TYPE_PANEL, dialog.findViewById(R.id.bannerPanelSwitch));
        switchByType.put(App.Type.TYPE_WEB, dialog.findViewById(R.id.bannerWebSwitch));
        final Map<App.Type, View> switchContainerByType = new HashMap<>();
        switchContainerByType.put(App.Type.TYPE_PHONE, dialog.findViewById(R.id.bannerPhoneContainer));
        switchContainerByType.put(App.Type.TYPE_VR, dialog.findViewById(R.id.bannerVrContainer));
        switchContainerByType.put(App.Type.TYPE_TV, dialog.findViewById(R.id.bannerTvContainer));
        switchContainerByType.put(App.Type.TYPE_PANEL, dialog.findViewById(R.id.bannerPanelContainer));
        switchContainerByType.put(App.Type.TYPE_WEB, dialog.findViewById(R.id.bannerWebsiteContainer));

        for (App.Type type : switchByType.keySet()) {
            if (Platform.getSupportedAppTypes(context).contains(type)) {
                Objects.requireNonNull(switchContainerByType.get(type)).setVisibility(View.VISIBLE);

                final Switch bSwitch = switchByType.get(type);
                if (bSwitch == null) continue;
                bSwitch.setChecked(App.typeIsBanner(type));
                bSwitch.setOnCheckedChangeListener((switchView, value) -> {
                    SettingsManager.setTypeBanner(type, value);
                    context.launcherService.forEachActivity(LauncherActivity::resetAdapters);
                });
            } else {
                Objects.requireNonNull(switchContainerByType.get(type)).setVisibility(View.GONE);
            }
        }

        // Names
        attachSwitchToSetting(dialog.findViewById(R.id.nameSquareSwitch),
                Settings.KEY_SHOW_NAMES_SQUARE, Settings.DEFAULT_SHOW_NAMES_SQUARE);

        attachSwitchToSetting(dialog.findViewById(R.id.nameBannerSwitch),
                Settings.KEY_SHOW_NAMES_BANNER, Settings.DEFAULT_SHOW_NAMES_BANNER);

        // Advanced button
        dialog.findViewById(R.id.advancedSettingsButton).setOnClickListener(view -> showAdvancedSettings());
        return dialog;
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public void showAdvancedSettings() {
        AlertDialog dialog = new BasicDialog<>(context, R.layout.dialog_settings_advanced).show();
        if (dialog == null) return;

        dialog.findViewById(R.id.dismissButton).setOnClickListener(view -> dialog.dismiss());

        if (Platform.isQuest(context)) {
            SeekBar alpha = dialog.findViewById(R.id.alphaSeekBar);
            alpha.setProgress(255 - context.dataStoreEditor.getInt(Settings.KEY_BACKGROUND_ALPHA, Settings.DEFAULT_ALPHA));
            alpha.post(() -> alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
                    context.dataStoreEditor.putInt(Settings.KEY_BACKGROUND_ALPHA, 255 - value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    context.refreshBackground();
                }
            }));
        } else dialog.findViewById(R.id.alphaLayout).setVisibility(View.GONE);

        // Group enabled state
        if (context.canEdit()) {
            // Can edit, show switch
            dialog.findViewById(R.id.editRequiredContaier).setVisibility(View.VISIBLE);
        } else {
            dialog.findViewById(R.id.editRequiredContaier).setVisibility(View.GONE);
        }

        // Advanced
        attachSwitchToSetting(dialog.findViewById(R.id.longPressEditSwitch),
                Settings.KEY_DETAILS_LONG_PRESS, Settings.DEFAULT_AUTO_HIDE_EMPTY);

        if (Platform.isVr(context)) {
            attachSwitchToSetting(dialog.findViewById(R.id.defaultLaunchOutSwitch),
                    Settings.KEY_DEFAULT_LAUNCH_OUT, Settings.DEFAULT_DEFAULT_LAUNCH_OUT);

            attachSwitchToSetting(dialog.findViewById(R.id.advancedSizingSwitch),
                    Settings.KEY_ADVANCED_SIZING, Settings.DEFAULT_ADVANCED_SIZING,
                    value -> showOneTimeWarningDialog(R.layout.dialog_info_launch_size,
                                                  Settings.KEY_SEEN_LAUNCH_SIZE_POPUP)
            );

            View defaultSettingsButton = dialog.findViewById(R.id.defaultLauncherSettingsButton);
            defaultSettingsButton.setOnClickListener((view) -> {
                AlertDialog minDialog = new BasicDialog<>(context, R.layout.dialog_info_set_default_launcher).show();
                assert minDialog != null;
                minDialog.findViewById(R.id.confirm).setOnClickListener(view1 -> {
                    final Intent intent = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
                    intent.setPackage("com.android.permissioncontroller");
                    minDialog.cancel();
                    context.startActivity(intent);
                });
                minDialog.findViewById(R.id.cancel).setOnClickListener(view1 -> minDialog.cancel());
            });
        } else {
            dialog.findViewById(R.id.extraFeaturesTitle).setVisibility(View.GONE);
            dialog.findViewById(R.id.defaultLauncherSettingsButton).setVisibility(View.GONE);
            dialog.findViewById(R.id.defaultLaunchOutSection).setVisibility(View.GONE);
            dialog.findViewById(R.id.advancedSizingSection).setVisibility(View.GONE);
        }

        // Default Browser Selection
        // Browser selection spinner
        final View launchBrowserSpinner = dialog.findViewById(R.id.launchBrowserSpinner);
        final TextView launchBrowserSpinnerText = dialog.findViewById(R.id.launchBrowserSpinnerText);

        launchBrowserSpinner.setVisibility(View.VISIBLE);

        final int[] defaultBrowserSelection = {context.dataStoreEditor.getInt(
                Settings.KEY_DEFAULT_BROWSER,
                SettingsManager.getDefaultBrowser()
        )};
        launchBrowserSpinnerText.setText(Settings.launchBrowserStrings[defaultBrowserSelection[0]]);
        launchBrowserSpinner.setOnClickListener((view) -> {
            // Cycle selection
            int index = defaultBrowserSelection[0];
            index = (index + 1) % Settings.launchBrowserStrings.length;
            // Skip quest browser if not on quest
            if (Settings.launchBrowserStrings[index] == R.string.browser_quest
                    && !Platform.isQuest(context)) index++;
            // Update text & store setting
            final int stringRes = Settings.launchBrowserStrings[index];
            launchBrowserSpinnerText.setText(stringRes);
            context.dataStoreEditor.putInt(Settings.KEY_DEFAULT_BROWSER, index);
            defaultBrowserSelection[0] = index;
        });

        // Search settings
        attachSwitchToSetting(dialog.findViewById(R.id.searchWebSwitch),
                Settings.KEY_SEARCH_WEB, Settings.DEFAULT_SEARCH_WEB);
        attachSwitchToSetting(dialog.findViewById(R.id.searchHiddenSwitch),
                Settings.KEY_SEARCH_HIDDEN, Settings.DEFAULT_SEARCH_HIDDEN);

        // Save/load settings
        View loadSettings = dialog.findViewById(R.id.loadSettingsButton);
        loadSettings.setAlpha(SettingsSaver.canLoad(context) ? 1F : 0.5F);
        loadSettings.setOnClickListener((view) -> {
            if (SettingsSaver.canLoad(context)) {
                dialog.dismiss();
                SettingsSaver.load(context);
            } else {
                BasicDialog.toast("Failed to find file!");
            }
        });

        View saveSettings = dialog.findViewById(R.id.saveSettingsButton);
        saveSettings.setOnClickListener((view) -> {
            SettingsSaver.save(context);
            loadSettings.setAlpha(1F);
        });

        // Save/load settings
        View loadGroupings = dialog.findViewById(R.id.loadGroupingsButton);
        loadGroupings.setAlpha(SettingsSaver.canLoadSort(context) ? 1F : 0.5F);
        loadGroupings.setOnClickListener((view) -> {
            if (SettingsSaver.canLoadSort(context)) {
                dialog.dismiss();
                SettingsSaver.loadSort(context);
            } else {
                BasicDialog.toast("Failed to find file!");
            }
        });

        View saveGroupings = dialog.findViewById(R.id.saveGroupingsButton);
        saveGroupings.setOnClickListener((view) -> {
            SettingsSaver.saveSort(context);
            loadGroupings.setAlpha(1F);
        });
    }



    /**
     * Attaches a toggle switch to a specific setting
     * @param toggle Switch ui element
     * @param setting Setting string key
     * @param def Default setting value
     */
    private void attachSwitchToSetting(Switch toggle, String setting,
                                       boolean def) {
        attachSwitchToSetting(toggle, setting, def, null);
    }
    /**
     * Attaches a toggle switch to a specific setting
     * @param toggle Switch ui element
     * @param setting Setting string key
     * @param def Default setting value
     * @param onSwitch Consumes the new value when it is changed, after writing to the datastore
     */
    private void attachSwitchToSetting(Switch toggle, String setting,
                                       boolean def, Consumer<Boolean> onSwitch) {
        toggle.setChecked(context.dataStoreEditor.getBoolean(setting, def));
        toggle.setOnCheckedChangeListener((compoundButton, value) -> {
            context.dataStoreEditor.putBoolean(setting, value);
            if (onSwitch != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                onSwitch.accept(value);
            context.launcherService.forEachActivity(LauncherActivity::refreshInterface);
        });
    }

    /**
     * Displays a one-time warning dialog
     * @param dialogResource ResId for the dialog; must contain a button with id of "confirm"
     * @param keySeenDialog Key to indicate the user has seen the dialog
     */
    private void showOneTimeWarningDialog(int dialogResource, String keySeenDialog) {
        if (!context.dataStoreEditor.getBoolean(keySeenDialog, false)) {
            AlertDialog subDialog = new BasicDialog<>(context, dialogResource).show();
            if (subDialog == null) return;
            subDialog.findViewById(R.id.confirm).setOnClickListener(view -> {
                context.dataStoreEditor.putBoolean(keySeenDialog, true);
                subDialog.dismiss();
            });
        }
    }

    private void showGroupSettings() {
        clearedSort = false;
        clearedGroups = false;

        AlertDialog dialog = new BasicDialog<>(context, R.layout.dialog_setting_reset_groups).show();
        if (dialog == null) return;

        View clearSort = dialog.findViewById(R.id.resortOnly);
        clearSort.setOnClickListener(view -> {
            if (!clearedSort) {
                Compat.clearSort(context);
                clearSort.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(context.getString(R.string.default_groups_resort_only_toast_main),
                        context.getString(R.string.default_groups_resort_only_toast_bold),
                        false);
            }
        });

        StringBuilder builder = new StringBuilder();
        for (App.Type type : Platform.getSupportedAppTypes(context)) {
            builder.append(App.getTypeString(context, type))
                    .append(" : ")
                    .append(App.getDefaultGroupFor(type))
                    .append("\n");
        }

        TextView info = dialog.findViewById(R.id.infoText);
        info.setText(context.getString(R.string.default_groups_info, builder.toString()));


        View clearDefaults = dialog.findViewById(R.id.resetGroups);
        clearDefaults.setOnClickListener(view -> {
            if (!clearedGroups) {
                Compat.resetDefaultGroups(context);
                clearDefaults.setAlpha(0.5f);
                clearedGroups = true;
                clearSort.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(context.getString(R.string.default_groups_reset_groups_toast_main),
                        context.getString(R.string.default_groups_reset_groups_toast_bold),
                        false);

                StringBuilder builder2 = new StringBuilder();
                for (App.Type type : Platform.getSupportedAppTypes(context)) {
                    builder2.append(App.getTypeString(context, type))
                            .append(" : ")
                            .append(App.getDefaultGroupFor(type))
                            .append("\n");
                }

                info.setText(context.getString(R.string.default_groups_info, builder2.toString()));
            }
        });

        dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
    }
    public static void showIconSettings(LauncherActivity a) {
        clearedIconCache = false;
        clearedIconCustom = false;

        AlertDialog dialog = new BasicDialog<>(a, R.layout.dialog_setting_reset_icons).show();
        if (dialog == null) return;

        View clearCache = dialog.findViewById(R.id.clearCache);
        clearCache.setOnClickListener(view -> {
            if (!clearedIconCache) {
                Compat.clearIconCache(a);
                clearCache.setAlpha(0.5f);
                clearedSort = true;

                BasicDialog.toast(a.getString(R.string.toast_cleared_icon_cache));
            }
        });

        View clearAll = dialog.findViewById(R.id.clearAll);
        clearAll.setOnClickListener(view -> {
            if (!clearedIconCustom) {
                Compat.clearIcons(a);
                clearAll.setAlpha(0.5f);
                clearedIconCustom = true;
                clearCache.setAlpha(0.5f);
                clearedIconCache = true;

                BasicDialog.toast(a.getString(R.string.toast_cleared_icon_all));
            }
        });

        dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
    }
}
