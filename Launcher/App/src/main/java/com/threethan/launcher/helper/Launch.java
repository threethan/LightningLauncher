package com.threethan.launcher.helper;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.AppData;
import com.threethan.launcher.data.PanelApplicationInfo;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.AddShortcutActivity;
import com.threethan.launcher.updater.BrowserUpdater;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This abstract class is dedicated to actually launching apps.
 * <p>
 * The helper function "getAppLaunchable" is also used by the App class to determine if an app
 * can possibly be launched.
 */
public abstract class Launch {
    protected static final String ACTION_ACTUALLY_SHORTCUT = "ACTION_ACTUALLY_SHORTCUT";

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
            Keyboard.hide(launcherActivity, launcherActivity.mainView);
        } catch (Exception ignored) {}

        Intent intent = getLaunchIntent(launcherActivity, app);

        if (intent == null) {
            Log.w("AppLaunch", "Package could not be launched (Uninstalled?): "
                    +app.packageName);
            launcherActivity.refreshPackages();
            return false;
        }

        // Browser Check
        if (Objects.equals(intent.getPackage(), Platform.BROWSER_PACKAGE)) {
            if (Platform.hasBrowser(launcherActivity)) {
                // Check for browser update. User probably won't see the prompt until closing, though.
                BrowserUpdater browserUpdater = new BrowserUpdater(launcherActivity);
                if (browserUpdater.getInstalledVersionCode() < BrowserUpdater.REQUIRED_VERSION_CODE) {
                    // If browser is required, but not installed
                    // Prompt installation
                    AlertDialog dialog = new BasicDialog<>(launcherActivity, R.layout.dialog_prompt_browser_update).show();
                    if (dialog == null) return false;
                    dialog.findViewById(R.id.cancel).setOnClickListener((view) -> dialog.dismiss());
                    dialog.findViewById(R.id.install).setOnClickListener((view) -> {
                        new BrowserUpdater(launcherActivity).checkAppUpdateAndInstall();
                        BasicDialog.toast(launcherActivity.getString(R.string.download_browser_toast_main),
                                launcherActivity.getString(R.string.download_browser_toast_bold), true);
                    });
                    return false;
                }
            } else {
                // If browser is required, but not installed
                // Prompt installation
                AlertDialog dialog = new BasicDialog<>(launcherActivity, R.layout.dialog_prompt_browser_install).show();
                if (dialog == null) return false;
                dialog.findViewById(R.id.cancel).setOnClickListener((view) -> dialog.dismiss());
                dialog.findViewById(R.id.install).setOnClickListener((view) -> {
                    new BrowserUpdater(launcherActivity).checkAppUpdateAndInstall();
                    BasicDialog.toast(launcherActivity.getString(R.string.download_browser_toast_main),
                            launcherActivity.getString(R.string.download_browser_toast_bold), true);
                });
                return false;
            }
        }

        final App.Type appType = App.getType(app);
        if (SettingsManager.
                getAppLaunchOut(app.packageName) ||
                appType == App.Type.TYPE_VR || appType == App.Type.TYPE_PANEL) {

            launcherActivity.launcherService.finishAllActivities();

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK );

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    startIntent(launcherActivity, intent);
                }
            }, 650);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    startIntent(launcherActivity, intent);
                }
            }, 800);
            return false;
        } else {
            startIntent(launcherActivity, intent);
            return true;
        }
    }
    private static void startIntent(LauncherActivity launcherActivity, Intent intent) {
        if (Objects.equals(intent.getAction(), ACTION_ACTUALLY_SHORTCUT)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                AddShortcutActivity.launchShortcut(launcherActivity, intent.getStringExtra("json"));
            }
        }
        else launcherActivity.startActivity(intent);
    }

    /**
     * Gets the intent used to actually launch the given app,
     * including workarounds for browsers & panel apps
     */
    @Nullable
    private static Intent getLaunchIntent(LauncherActivity activity, ApplicationInfo app) {

        // Ignore apps which don't work or should be excluded
        if (app.packageName.startsWith(activity.getPackageName())) return null;
        if (AppData.invalidAppsList.contains(app.packageName)) return null;

        PackageManager pm = activity.getPackageManager();

        // Detect panel apps
        if (App.isAppOfType(app, App.Type.TYPE_PANEL)) {
            String uri = app.packageName;
            if (uri.startsWith(PanelApplicationInfo.packagePrefix))
                uri = uri.replace(PanelApplicationInfo.packagePrefix, "");

            Intent panelIntent = new Intent(Intent.ACTION_VIEW);
            panelIntent.setComponent(new ComponentName(
                    "com.oculus.vrshell", "com.oculus.vrshell.MainActivity"));
            panelIntent.setData(Uri.parse(uri));

            // Special case for events, which depends on explore
            if (app.packageName.equals("systemux://events") &&
                    !App.isPackageEnabled(activity, AppData.EXPLORE_PACKAGE)) return null;

            if (pm.resolveActivity(panelIntent, 0) != null) return panelIntent;
            else return null;
        }

        // Detect shortcuts (must check before websites)
        if (App.isShortcut(app)) {
            Intent intent = new Intent(ACTION_ACTUALLY_SHORTCUT);
            intent.putExtra("json", app.packageName.replaceFirst("json://", ""));
            return intent;
        }

        // Detect websites
        if (App.isWebsite(app)) {
            Intent intent;
            if (app.packageName.startsWith("http://") || (app.packageName.startsWith("https://"))) {
                final int browserIndex
                        = activity.dataStoreEditor.getInt(Settings.KEY_LAUNCH_BROWSER + app.packageName, 0);
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final int browserStringRes = Settings.launchBrowserStrings[browserIndex];
                switch (browserStringRes) {
                    case (R.string.browser_quest):
                        intent.setPackage("com.oculus.browser");
                        intent.setComponent(
                                new ComponentName("com.oculus.browser",
                                        "com.oculus.browser.PanelActivity"));
                        break;
                    case (R.string.browser_system):
                        break;
                    default:
                        intent.setPackage(Platform.BROWSER_PACKAGE);
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


        // Otherwise android TV settings is not recognized
        if (Objects.equals(app.packageName, "com.android.tv.settings"))
            return new Intent(android.provider.Settings.ACTION_SETTINGS);

        // Prefer launching as android TV app
        Intent tvIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
        if (Platform.isTv(activity) && tvIntent != null) return tvIntent;

        // Chainload for advanced launch options
        if (SettingsManager.getShowAdvancedSizeOptions(activity)
                && App.getType(app) == App.Type.TYPE_PHONE &&
                SettingsManager.getAppLaunchOut(app.packageName)) {

            int index = activity.dataStoreEditor.getInt(Settings.KEY_LAUNCH_SIZE + app.packageName, 1);
            // Index of 1 is normal launch own
            if (index != 1) {
                Intent chainIntent = new Intent(activity, Settings.launchSizeClasses[index]);
                chainIntent.putExtra("app", app);
                return chainIntent;
            }
        }

        // Get normal launch intent
        final Intent normalIntent = pm.getLaunchIntentForPackage(app.packageName);
        if (normalIntent == null && tvIntent != null) return tvIntent;
        return normalIntent;
    }

    /**
     * Checks if an app can be launched. Similar to getLaunchIntent, but faster
     * @return True if app can be launched & is supported
     */
    public static boolean checkLaunchable(LauncherActivity activity, ApplicationInfo app) {
        // Ignore apps which don't work or should be excluded
        if (app.packageName.startsWith(activity.getPackageName())) return false;
        if (AppData.invalidAppsList.contains(app.packageName)) return false;

        PackageManager pm = activity.getPackageManager();

        if (App.isAppOfType(app, App.Type.TYPE_PANEL)) return true;
        if (App.isAppOfType(app, App.Type.TYPE_WEB)) return true;

        // Special case
        if (Objects.equals(app.packageName, "com.android.tv.settings")) return true;

        // Actually check now
        if (pm.getLaunchIntentForPackage(app.packageName) != null) return true;
        return pm.getLeanbackLaunchIntentForPackage(app.packageName) != null;
    }
}
