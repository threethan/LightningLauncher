package com.threethan.launchercore.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;

import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.DelayLib;

import java.util.Objects;

public abstract class Launch {

    /**
     * Gets the intent to launch a given app
     * @param app App to launch
     * @return Intent to launch if found, null if failed
     */
    @Nullable
    public static Intent getLaunchIntent(ApplicationInfo app) {
        if (app.packageName.startsWith(Core.context().getPackageName())) return null;
        if (Platform.excludedPackageNames.contains(app.packageName)) return null;

        if (app.packageName.equals("com.android.settings") && Platform.cantLaunchSettings()) {
            // Get app info intent for the actual settings app
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:com.android.settings"));
            intent.setPackage("com.android.settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        }

        PackageManager pm = Core.context().getPackageManager();

        if (Platform.isQuest() && App.getType(app) == App.Type.PANEL) {
            Intent intent = pm.getLaunchIntentForPackage("com.oculus.vrshell");
            assert intent != null;
            intent.setData(Uri.parse(app.packageName));
            return intent;
        }

        if (App.getType(app) == App.Type.WEB) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }

        final Intent defaultIntent  = pm.getLaunchIntentForPackage(app.packageName);
        final Intent leanbackIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
        if (Platform.isQuest() && PlatformExt.useVrOsChainLaunch()) {
            final Intent intent = defaultIntent != null ? defaultIntent : leanbackIntent;
            if (intent != null) return getVrOsLaunchIntent(app.packageName);
            else return null;
        } else if (Platform.isTv()) {
            if (Objects.equals(app.packageName, "com.android.tv.settings"))
                return new Intent(android.provider.Settings.ACTION_SETTINGS);
            return leanbackIntent != null ? leanbackIntent : defaultIntent;
        } else {
            return defaultIntent != null ? defaultIntent : leanbackIntent;
        }
    }

    /** Gets an intent to launch an application using vrOS. Not tested before v69. */
    public static Intent getVrOsLaunchIntent(String packageName) {
        if (!Platform.supportsVrOsChainLaunch()) {
            Log.e("Launch", "Attempted to get vrOs launch intent on unsupported version!" +
                    "\nThe standard intent will be returned instead.");
            return getLaunchIntent(App.infoFor(packageName));
        }
        Intent intent = new Intent();
        intent.setAction("com.oculus.vrshell.intent.action.LAUNCH");
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setData(Uri.parse("apk://"+packageName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.oculus.vrshell");
        intent.setComponent(new ComponentName("com.oculus.vrshell", "com.oculus.vrshell.MainActivity"));
        return intent;
    }

    /** Launch a custom activity intent - in it's own window, if applicable */
    public static void launchInOwnWindow(Intent intent, Activity activity,
                                         boolean allowNewVrOsMultiWindow) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.removeFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        Runnable onPostDestroy = () -> {
            try {
                activity.startActivity(intent);
            } catch (SecurityException ignored) {
                BasicDialog.toast("No permission to launch app");
            }
            if (Platform.supportsNewVrOsMultiWindow() && allowNewVrOsMultiWindow) {
                PackageManager pm = Core.context().getPackageManager();
                Intent relaunch = pm.getLaunchIntentForPackage(activity.getPackageName());
                DelayLib.delayed(() -> activity.startActivity(relaunch), 550);
                DelayLib.delayed(() -> {
                    try {
                        activity.startActivity(intent);
                    } catch (SecurityException ignored) {
                        BasicDialog.toast("No permission to launch app");
                    }
                }, 2000);
            }
        };

        if (!Platform.isVr()) {
            onPostDestroy.run();
        } else if (activity instanceof LaunchingActivity launchingActivity) {
            launchingActivity.setOnPostDestroy(onPostDestroy);
            launchingActivity.finishAffinity();
        } else {
            DelayLib.delayed(onPostDestroy, 50);
            activity.finishAffinity();
        }
    }
    /** A subclass of androidx ComponentActivity which supports launching vrOS apps without delay */
    public static class LaunchingActivity extends ComponentActivity {
        private Runnable onPostDestroy = null;

        /** Sets a runnable to be called after the activity is destroyed */
        protected void setOnPostDestroy(Runnable onPostDestroy) {
            this.onPostDestroy = onPostDestroy;
        }
        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (onPostDestroy != null) new Handler().post(onPostDestroy);
        }
    }
}
