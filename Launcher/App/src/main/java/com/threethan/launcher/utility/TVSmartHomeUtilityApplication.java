package com.threethan.launcher.utility;

import android.content.ComponentName;
import android.content.Intent;

import com.threethan.launcher.R;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.Platform;

/**
 * An instance of UtilityApplicationInfo which provides an easy way to open the ATV home panel
 */
public class TVSmartHomeUtilityApplication extends UtilityApplicationInfo {
    private TVSmartHomeUtilityApplication() {
        super("builtin://tv-smart-home", R.drawable.ai_tv_smart_home);
    }
    private static TVSmartHomeUtilityApplication instance;
    public static TVSmartHomeUtilityApplication getInstance() {
        if (instance== null) instance = new TVSmartHomeUtilityApplication();
        return instance;
    }

    public static boolean shouldShow() {
        return Platform.isTv() && App.packageExists("com.google.android.apps.tv.dreamx");
    }

    public void launch() {
        Intent intent = new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME");
        intent.setComponent(new ComponentName(
                "com.google.android.apps.tv.dreamx",
                "com.google.android.libraries.tv.smarthome.core.SmartHomeMainActivity"
        ));
        intent.setAction("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            Core.context().startActivity(intent);
        } catch (Exception ignored) {}
    }
}