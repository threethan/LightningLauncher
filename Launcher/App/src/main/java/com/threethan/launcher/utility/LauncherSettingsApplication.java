package com.threethan.launcher.utility;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.SettingsDialog;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;

/**
 * An instance of UtilityApplicationInfo which provides an easy way to open launcher settings.
 */
public class LauncherSettingsApplication extends UtilityApplicationInfo {
    private LauncherSettingsApplication() {
        super("builtin://launcher-settings", R.drawable.ic_settings);
    }
    private static LauncherSettingsApplication instance;
    public static LauncherSettingsApplication getInstance() {
        if (instance== null) instance = new LauncherSettingsApplication();
        return instance;
    }

    public void launch() {
        new SettingsDialog(LauncherActivity.getForegroundInstance()).show();
    }
}