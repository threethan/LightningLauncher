package com.threethan.launcher.helper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.AddShortcutActivity;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.updater.BrowserUpdater;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.CustomDialog;
import com.threethan.launchercore.util.Keyboard;
import com.threethan.launchercore.util.Launch;
import com.threethan.launchercore.util.Platform;

import java.util.Objects;

/**
 * This abstract class is dedicated to actually launching apps.
 * <p>
 * The helper function "getAppLaunchable" is also used by the App class to determine if an app
 * can possibly be launched.
 */
public abstract class LaunchExt extends Launch {
    protected static final String ACTION_ACTUALLY_SHORTCUT = "ACTION_ACTUALLY_SHORTCUT";
    // Must match: arrays.xml -> advance_launch_browsers
    private static final int LAUNCH_BROWSER_SYSTEM = 1;
    private static final int LAUNCH_BROWSER_QUEST = 2;
    private static final int LAUNCH_BROWSER_QUEST_SHORTCUT = 3;

    /**
     * Launches a given app, checking various configuration options in the process
     * @param launcherActivity The activity to launch from
     * @param app The app to launch
     * @return True if the app was launched
     */
    public static boolean launchApp(LauncherActivity launcherActivity, ApplicationInfo app) {
        // Apply any pending preference changes before launching
        try {
            // This is unlikely to fail, but it shouldn't stop us from launching if it somehow does
            Keyboard.hide(launcherActivity.mainView);
        } catch (Exception ignored) {}

        if (!app.enabled) {
            AppExt.openInfo(launcherActivity, app.packageName);
            return false;
        }

        Intent intent = getIntentForLaunch(launcherActivity, app);

        if (intent == null) {
            Log.w("AppLaunch", "Package could not be launched (Uninstalled?): "
                    +app.packageName);
            launcherActivity.refreshPackages();
            return false;
        }

        // Browser Check
        if (Objects.equals(intent.getPackage(), PlatformExt.BROWSER_PACKAGE)) {
            if (!PlatformExt.hasBrowser(launcherActivity) ||
                    new BrowserUpdater(launcherActivity).getInstalledVersionCode()
                            < BrowserUpdater.REQUIRED_VERSION_CODE) {
                // Check for browser update. User probably won't see the prompt until closing, though.
                BrowserUpdater browserUpdater = new BrowserUpdater(launcherActivity);
                if (browserUpdater.getInstalledVersionCode() < BrowserUpdater.REQUIRED_VERSION_CODE) {
                    new CustomDialog.Builder(launcherActivity)
                            .setTitle(R.string.warning)
                            .setMessage(PlatformExt.hasBrowser(launcherActivity)
                                    ? R.string.update_browser_message
                                    : R.string.download_browser_message)
                            .setPositiveButton(R.string.addons_install, (d, w) -> {
                                new BrowserUpdater(launcherActivity).checkAppUpdateAndInstall();
                                BasicDialog.toast(launcherActivity.getString(R.string.download_browser_toast_main),
                                        launcherActivity.getString(R.string.download_browser_toast_bold), true);
                            })
                            .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                            .show();

                    return false;
                }
            }
        }

        // Apply tuning via Quest Game Tuner (no effect unless Quest Game Tuner >= 1.5 is installed)
        QuestGameTuner.applyTuning(app.packageName);

        if (Platform.isTv()) {
            startIntent(launcherActivity, intent);
            return true;
        }

        final boolean customSize = SettingsManager.getAppLaunchSize(app.packageName) > 0;

        if (PlatformExt.isOldVrOs() && !customSize) {
            // Launching method for Quest 1
            final App.Type appType = App.getType(app);
            if (appType == App.Type.TV || appType == App.Type.PHONE) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                launchInOwnWindow(intent, launcherActivity, false);
            } else {
                launcherActivity.launcherService.forEachActivity(Activity::finishAndRemoveTask);
                DelayLib.delayed(() -> startIntent(launcherActivity, intent), 650);
            }
        } else if ((customSize || !PlatformExt.useVrOsChainLaunch())
                && !(app instanceof UtilityApplicationInfo)) {
            Intent chain = getIntentForLaunch(launcherActivity, app);
            assert chain != null;
            launchInOwnWindow(chain, launcherActivity,
                    !customSize && PlatformExt.useNewVrOsMultiWindow());
        } else {
            final App.Type appType = App.getType(app);
            if (appType == App.Type.PANEL) startIntent(launcherActivity, intent);
            else if (appType == App.Type.WEB) launchInOwnWindow(intent, launcherActivity,
                    PlatformExt.useNewVrOsMultiWindow());
            else if (appType == App.Type.UTILITY) ((UtilityApplicationInfo) app).launch();
            else launcherActivity.startActivity(getIntentForLaunchVrOs(app));
            if (!PlatformExt.useNewVrOsMultiWindow()) launcherActivity.finishAffinity();
            return Platform.isTv() || (Platform.isQuest() && appType.equals(App.Type.VR));
        }
        return true;
    }

    private static Intent getIntentForLaunchVrOs(ApplicationInfo app) {
        Intent intent = new Intent();
        intent.setAction("com.oculus.vrshell.intent.action.LAUNCH");
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setData(Uri.parse("apk://"+app.packageName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.oculus.vrshell");
        intent.setComponent(new ComponentName("com.oculus.vrshell", "com.oculus.vrshell.MainActivity"));
        return intent;
    }

    private static void startIntent(LauncherActivity launcherActivity, Intent intent) {
        if (Objects.equals(intent.getAction(), ACTION_ACTUALLY_SHORTCUT)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            AddShortcutActivity.launchShortcut(launcherActivity, intent.getStringExtra("json"));
        else launcherActivity.startActivity(intent);
    }

    /**
     * Gets the intent used to actually launch the given app,
     * including workarounds for browsers & panel apps
     */
    @Nullable
    private static Intent getIntentForLaunch(LauncherActivity activity, ApplicationInfo app) {
        // Ignore apps which don't work or should be excluded
        if (app.packageName.equals(activity.getPackageName())
                || app.packageName.contains("com.threethan.launcher.service")) return null;

        // Detect websites
        if (App.isWebsite(app.packageName)) {
            Intent intent;
            if (app.packageName.startsWith("http://") || (app.packageName.startsWith("https://"))) {
                final int browserIndex
                        = activity.dataStoreEditor.getInt(Settings.KEY_LAUNCH_BROWSER + app.packageName,
                        activity.dataStoreEditor.getInt(Settings.KEY_DEFAULT_BROWSER, 0));
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Reference: arrays.xml -> advance_launch_browsers
                switch (browserIndex) {
                    case (LAUNCH_BROWSER_QUEST):
                        intent.setPackage("com.oculus.browser");
                        intent.setComponent(
                                new ComponentName("com.oculus.browser",
                                        "com.oculus.browser.PanelActivity"));
                        break;

                    case (LAUNCH_BROWSER_QUEST_SHORTCUT):
                        intent.setPackage("com.oculus.browser");
                        intent.setComponent(
                                new ComponentName("com.oculus.browser",
                                        "com.oculus.browser.ShortcutLauncher"));

                        intent.putExtra("org.chromium.chrome.browser.webapp_url", app.packageName);
                        intent.putExtra("org.chromium.chrome.browser.webapp_short_name", App.getLabel(app));
                        intent.putExtra("org.chromium.chrome.browser.webapp_display_mode", 3);
                        intent.putExtra("REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB", true);
                        break;

                    case (LAUNCH_BROWSER_SYSTEM):
                        break;

                    default:
                        intent.setPackage(PlatformExt.BROWSER_PACKAGE);
                        break;
                }
                return intent;
            } else {
                // Non-web intent
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(app.packageName));
            }
            return intent;
        }

        Intent li = getLaunchIntent(app);
        // Chain-load for advanced launch options
        if (AppExt.getType(app) == App.Type.PHONE && li != null) {
            int index = SettingsManager.getAppLaunchSize(app.packageName);
            if (index > 0) {
                Intent chainIntent = new Intent(activity, Settings.launchSizeClasses[index]);
                chainIntent.putExtra("app", app);
                return chainIntent;
            }
        }

        return li;
    }

    /** Launches an URL using a view intent, in a new window.
     * If activity is null, it will attempt to close the foreground instance. */
    public static void launchUrl(@Nullable Activity activity, String url, boolean forceChromium) {
        if (activity == null && LauncherActivity.getForegroundInstance() != null)
            activity = LauncherActivity.getForegroundInstance();
        if (activity != null) activity.finishAffinity();

        Intent openURL = new Intent(Intent.ACTION_VIEW);
        openURL.setData(Uri.parse(url));
        openURL.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (forceChromium && Platform.isQuest())
            openURL.setComponent(
                new ComponentName("com.oculus.browser",
                        "com.oculus.browser.PanelActivity"));

        DelayLib.delayed(() -> Core.context().startActivity(openURL), 50);

        if (PlatformExt.useNewVrOsMultiWindow()) {
            PackageManager pm = Core.context().getPackageManager();
            Intent relaunch = pm.getLaunchIntentForPackage(Core.context().getPackageName());
            DelayLib.delayed(() -> Core.context().startActivity(relaunch), 550);
        }
    }
}
