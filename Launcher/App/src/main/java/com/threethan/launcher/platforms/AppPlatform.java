package com.threethan.launcher.platforms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;

public class AppPlatform extends AbstractPlatform {
    @Override
    public boolean isSupported(Context context) {
        return true;
    }

    @Override
    public void runApp(Context context, ApplicationInfo appInfo) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
        context.startActivity(launchIntent);
    }
}
