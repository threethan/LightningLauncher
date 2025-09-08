package com.threethan.launcher.utility;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;
import com.threethan.launchercore.util.Platform;

/**
 * An instance of UtilityApplicationInfo which provides an easy way to install APKs on v74+
 */
public class ApkInstallerUtilityApplication extends UtilityApplicationInfo {
    private ApkInstallerUtilityApplication() {
        super("builtin://apk-install", R.drawable.ic_installer);
    }
    private static ApkInstallerUtilityApplication instance;
    public static ApkInstallerUtilityApplication getInstance() {
        if (instance== null) instance = new ApkInstallerUtilityApplication();
        return instance;
    }

    public static boolean shouldShow() {
        return Platform.getVrOsVersion() >= 74;
    }

    public void launch() {
        BasicDialog.toast(Core.context().getString(R.string.apk_installer_tip));

        if (!BasicDialog.validateVariantWithNotify()) return;

        if (LauncherActivity.getForegroundInstance() != null)
            LauncherActivity.getForegroundInstance()
                    .showFilePicker(LauncherActivity.FilePickerTarget.APK);
    }
}