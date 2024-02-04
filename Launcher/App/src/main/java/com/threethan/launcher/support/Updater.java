package com.threethan.launcher.support;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.threethan.launcher.R;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.Dialog;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.lib.FileLib;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * The automatic updater, instanced and called on launch. It handles update checks, downloads, and
 * install intents. It's somewhat app-specific, but feel free to adapt in for your own use.
 * <p>
 * It also handles downloading/updating/installing addons, though these must be manually checked
 * <p>
 * Credit to @Basti for code which checks github for updates
 */
public class Updater {
    private static final String UPDATE_URL = "https://api.github.com/repos/threethan/LightningLauncher/releases/latest";
    private static final String TEMPLATE_URL = "https://github.com/threethan/LightningLauncher/releases/download/%s/%s.apk";
    public static final String ADDON_RELEASE_TAG = "addons7.0.0";
    public static final String EXCLUDE_VERSION = "6.3.0"; // Ignored by the updater
    public static final String BROWSER_VERSION = "1.0.0";
    private static final String UPDATE_NAME = "LightningLauncher";
    public static final String TAG_FACEBOOK_SHORTCUT = "TAG_FACEBOOK_SHORTCUT";
    public static final String TAG_MONDAY_SHORTCUT = "TAG_MONDAY_SHORTCUT";
    public static final String TAG_APP_LIBRARY_SHORTCUT = "TAG_APP_LIBRARY_SHORTCUT";
    public static final String TAG_PEOPLE_SHORTCUT = "TAG_PEOPLE_SHORTCUT";
    public static final String TAG_HORIZON_FEED_SHORTCUT = "TAG_FEED_SHORTCUT";
    public static final String TAG_BROWSER = "TAG_BROWSER";
    public static final String TAG_ANDROID_TV_SHORTCUT = "TAG_ANDROID_TV_SHORTCUT";
    public static final String APK_DIR = "/Content/TemporaryDownloadedApk/";
    public static boolean anyDialogVisible = false;
    public static final Addon[] addons = {
            new Addon(TAG_FACEBOOK_SHORTCUT, "ShortcutFacebook", "com.facebook.facebookvr", "6.3.0", false),
            new Addon(TAG_MONDAY_SHORTCUT, "ShortcutMonday", "oculuspwa.auth.monday.com", "6.3.0", false),
            new Addon(TAG_PEOPLE_SHORTCUT, "ShortcutPeople", "com.threethan.launcher.service.people", "7.0.0", true),
            new Addon(TAG_APP_LIBRARY_SHORTCUT, "ShortcutAppLibrary", "com.threethan.launcher.service.library", "7.0.0", true),
            new Addon(TAG_HORIZON_FEED_SHORTCUT, "ShortcutHorizonFeed", "com.threethan.launcher.service.explore", "7.0.0", true),
            new Addon(TAG_BROWSER, "LightningBrowser", "com.threethan.browser", BROWSER_VERSION, false,
                    "https://github.com/threethan/LightningBrowser/releases/download/"+BROWSER_VERSION+"/LightningBrowser.apk", true),
            new Addon(TAG_ANDROID_TV_SHORTCUT, "LM (ATV) - 1.0.4", "com.wolf.google.lm", "1.0.4", false,
                    "https://xdaforums.com/attachments/lm-atv-1-0-4-apk.5498333/"),
    };
    private static final String TAG = "Lightning Launcher Updater";
    private final RequestQueue requestQueue;
    private final PackageManager packageManager;
    private final Activity activity;
    private static final String KEY_IGNORED_UPDATE_VERSION = "UPDATER_IGNORED_UPDATE_VERSION";
    private static final String KEY_UPDATE_AVAILABLE = "UPDATER_UPDATE_AVAILABLE";
    public static final int STATE_NOT_INSTALLED = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_HAS_UPDATE = 2;
    public static final int STATE_INACTIVE = 3;
    String latestVersionTag;
    private AlertDialog downloadingDialog;

    public Updater(Activity activity) {
        this.activity = activity;
        this.requestQueue = Volley.newRequestQueue(activity);
        this.packageManager = activity.getPackageManager();
    }
    public Updater(LauncherActivity activity) {
        this.activity = activity;
        this.requestQueue = Volley.newRequestQueue(activity);
        this.packageManager = activity.getPackageManager();
    }
    public void checkForAppUpdate() {
        checkLatestVersion(this::storeLatestVersionAndPrompt);
    }
    @Nullable
    private Addon getAddon(String tag) {
        for (Addon a:addons)
            if (a.match(tag))
                return a;
        return null;
    }
    public int getAddonState(String tag) {
        Addon addon = getAddon(tag);
        if (addon == null) return STATE_NOT_INSTALLED;
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(
                    addon.packageName, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return STATE_NOT_INSTALLED;
        }
        if (!packageInfo.versionName.equals(addon.latestVersion)) return STATE_HAS_UPDATE;
        if (addon.accessibilityService) {
            AccessibilityManager am = (AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            for (AccessibilityServiceInfo enabledService : enabledServices) {
                ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
                if (enabledServiceInfo.packageName.equals(addon.packageName))
                    return STATE_ACTIVE;
            }
            return STATE_INACTIVE;
        } else return STATE_ACTIVE;
    }
    public void uninstallAddon(Activity activity, String tag) {
        Addon addon = getAddon(tag);
        if (addon == null) return;
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        intent.setData(Uri.parse("package:" + addon.packageName));
        activity.startActivity(intent);
    }
    public void installAddon(String tag) {
        Addon addon = getAddon(tag);
        if (addon == null) return;
        Log.v(TAG, "Attempting to install addon "+tag);
        attempts = 3;
        downloadUpdate(addon.downloadName, ADDON_RELEASE_TAG, addon.overrideUrl);
    }
    public void updateAppEvenIfSkipped() {
        Compat.getDataStore(activity).removeString(KEY_IGNORED_UPDATE_VERSION);
        attempts = 6;
        downloadUpdate(UPDATE_NAME);
    }
    protected void storeLatestVersionAndPrompt(String tagName) {
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(
                    activity.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        boolean appHasUpdate = !(packageInfo.versionName).equals(tagName) &&
                !(EXCLUDE_VERSION).equals(tagName);

        if (appHasUpdate) {
            Log.v(TAG, "New version available!");
            Compat.getDataStore(activity).putBoolean(KEY_UPDATE_AVAILABLE, true);
            if (tagName.equals(Compat.getDataStore(activity).getString(KEY_IGNORED_UPDATE_VERSION, null))) return;
            showAppUpdateDialog(packageInfo.versionName, tagName);
        } else {
            Log.i(TAG, "App is up to date :)");
            Compat.getDataStore(activity).putBoolean(KEY_UPDATE_AVAILABLE, false);
            // Clear downloaded APKs
            FileLib.delete(Objects.requireNonNull(activity.getExternalFilesDir(Updater.APK_DIR)));
        }
    }

    public void checkLatestVersion(Response.Listener<String> callback) {
        if (anyDialogVisible) return; // Hide if already shown
        StringRequest updateRequest = new StringRequest(
                Request.Method.GET, UPDATE_URL,
                (response -> handleUpdateResponse(response, callback)),
                (this::handleUpdateError));
        requestQueue.add(updateRequest);
    }

    private void handleUpdateResponse(String response, @Nullable Response.Listener<String> callback) {
        try {
            JSONObject latestReleaseJson = new JSONObject(response);
            String tagName = latestReleaseJson.getString("tag_name");
            if (callback != null) callback.onResponse(tagName);
            latestVersionTag = tagName;
        } catch (JSONException e) {
            Log.w(TAG, "Received invalid JSON", e);
        }
    }
    private void handleUpdateError(VolleyError error) {
        Log.w(TAG, "Couldn't get update info", error);
    }

    private void showAppUpdateDialog(String curName, String newName) {
        try {
            attempts = 4;
            AlertDialog.Builder updateDialogBuilder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            updateDialogBuilder.setTitle(R.string.update_title);
            updateDialogBuilder.setMessage(activity.getString(R.string.update_content, curName, newName));
            updateDialogBuilder.setPositiveButton(R.string.update_button, (dialog, which) -> downloadUpdate(UPDATE_NAME));
            updateDialogBuilder.setNegativeButton(R.string.update_skip_button, (dialog, which) -> skipUpdate(newName));
            updateDialogBuilder.setOnDismissListener(di -> Updater.anyDialogVisible = false);
            updateDialogBuilder.show();
            anyDialogVisible = true;
        } catch (Exception ignored) {}
    }

    public static boolean isMainUpdateAvailable(Context context) {
        return Compat.getDataStore(context).getBoolean(KEY_UPDATE_AVAILABLE, false);
    }
    public void skipUpdate(String versionTag) {
        AlertDialog.Builder skipDialogBuilder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        skipDialogBuilder.setTitle(activity.getString(R.string.update_skip_title, versionTag));
        skipDialogBuilder.setMessage(R.string.update_skip_content);
        skipDialogBuilder.setPositiveButton(R.string.update_skip_confirm_button, (dialog, i) -> {
            Compat.getDataStore(activity).putString(KEY_IGNORED_UPDATE_VERSION, versionTag);
            Dialog.toast(activity.getString(R.string.update_skip_toast), versionTag, false);
            dialog.dismiss();
        });
        skipDialogBuilder.setNegativeButton(R.string.update_skip_cancel_button, ((dialog, i) -> dialog.dismiss()));
        skipDialogBuilder.setOnDismissListener(di -> Updater.anyDialogVisible = false);
        skipDialogBuilder.show();
        anyDialogVisible = true;
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Can't be fixed on this android API
    public void downloadUpdate(String apkName) {
        downloadSucceeded = false;
        if (latestVersionTag == null) {
            Log.w(TAG, "Latest version tag was null!" +
                    "ill try to get it again, but this download will probably fail");
            checkLatestVersion(response -> handleUpdateResponse(response, null));
        }
        downloadUpdate(apkName, latestVersionTag, null);
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Can't be fixed on this API version
    private void downloadUpdate(String apkName, String tagName, @Nullable String overrideUrl) {
        downloadSucceeded = false;
        downloadingName = apkName;
        downloadingTag = tagName;

        String url = String.format(TEMPLATE_URL, downloadingTag, apkName);
        if (overrideUrl != null) url = overrideUrl;

        Log.v(TAG, "Downloading from url "+url);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Downloading Update");   //appears the same in Notification bar while downloading
        request.setTitle("Lightning Launcher Auto-Updater");

        request.setDestinationInExternalFilesDir(activity, APK_DIR, apkName+latestVersionTag+".apk");

        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadingDialog == null) {
            AlertDialog.Builder updateDialogBuilder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            updateDialogBuilder.setTitle(R.string.update_downloading_title);
            updateDialogBuilder.setMessage(R.string.update_downloading_content);
            updateDialogBuilder.setNegativeButton(R.string.update_hide_button, (dialog, which) -> dialog.cancel());
            updateDialogBuilder.setOnDismissListener(di -> Updater.anyDialogVisible = false);
            updateDialogBuilder.show();
            anyDialogVisible = true;
            downloadingDialog = updateAlertDialog;
        }

        activity.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        manager.enqueue(request);
    }
    String downloadingTag;
    String downloadingName;
    boolean downloadSucceeded;
    AlertDialog updateAlertDialog;
    BroadcastReceiver onDownloadComplete =new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (updateAlertDialog != null) updateAlertDialog.dismiss();
            installUpdate(downloadingName);
        }
    };

    static int attempts = 8;
    public void installUpdate(String apkName) {
        File path = activity.getApplicationContext().getExternalFilesDir(APK_DIR);
        File file = new File(path, apkName+latestVersionTag+".apk");

        if (downloadSucceeded) return;
        if (file.exists()) {
            downloadSucceeded = true;
            // provider is already included in the imagepicker lib
            Uri apkURI = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".imagepicker.provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

            if (downloadingDialog != null) downloadingDialog.dismiss();
            activity.startActivity(intent);
        } else {
            if (attempts > 0) {
                Log.w(TAG, "Failed to download APK! Will keep trying " + attempts + " more times");
                downloadUpdate(apkName);
                attempts --;
            } else {
                AlertDialog.Builder failedDownloadDialogBuilder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                failedDownloadDialogBuilder.setTitle(R.string.update_failed_title);
                failedDownloadDialogBuilder.setMessage(R.string.update_failed_content);
                failedDownloadDialogBuilder.setNegativeButton(R.string.update_hide_button, (dialog, which) -> dialog.cancel());
                failedDownloadDialogBuilder.setOnDismissListener(di -> Updater.anyDialogVisible = false);
                failedDownloadDialogBuilder.show();
                anyDialogVisible = true;
            }
        }
    }
}