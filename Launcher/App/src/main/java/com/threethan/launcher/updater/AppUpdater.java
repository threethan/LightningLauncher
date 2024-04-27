package com.threethan.launcher.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.lib.FileLib;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * General Purpose App Updater which check for and pulls updates from GitHub
 */
public abstract class AppUpdater extends RemotePackageUpdater {
    public static final String URL_GITHUB_RELEASE_TEMPLATE =
            "https://github.com/%s/releases/download/%s/%s.apk";

    private static final String URL_GITHUB_API_TEMPLATE =
            "https://api.github.com/repos/%s/releases/latest";

    private final RequestQueue requestQueue;

    private static boolean updateAvailable = false;

    /**
     * Gets the package name of the app
     * @return Package name of the main app
     */
    protected String getAppPackageName() {
        return activity.getPackageName();
    }

    /**
     * Gets the display name of the app (used for popups)
     * @return Display name of the main app
     */
    protected abstract String getAppDisplayName();

    /**
     * Gets the download name of the app. Also used as a display name.
     * @return Name of the apk to download from the latest release, excluding .apk extension
     */
    protected abstract String getAppDownloadName();
    private RemotePackage getAppPackage(String releaseTag) {
        return new RemotePackage(getAppPackageName(), releaseTag,
                String.format(URL_GITHUB_RELEASE_TEMPLATE,
                        getGitRepo(), releaseTag, getAppDownloadName())) {
            @NonNull
            @Override
            public String toString() {
                return getAppDisplayName();
            }
        };
    }

    /**
     * Gets the github repo of the app
     * @return GitHub repo in the format myacct/my-app-repo
     */
    protected abstract String getGitRepo();

    public AppUpdater(Activity activity) {
        super(activity);
        this.requestQueue = Volley.newRequestQueue(activity);
    }

    /**
     * Shows a dialog prompting the user to download & install a main app update
     * @param currentVersion Current launcher version name
     * @param newVersion New launcher version name
     */
    private void showAppUpdateDialog(String currentVersion, String newVersion) {
        try {
            AlertDialog.Builder updateDialogBuilder =
                    new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            updateDialogBuilder.setTitle(R.string.update_title);
            updateDialogBuilder.setMessage(activity.getString(R.string.update_content, currentVersion, newVersion));

            updateDialogBuilder.setPositiveButton(R.string.update_button, (dialog, which) ->
                    downloadPackage(getAppPackage(newVersion)));
            updateDialogBuilder.setNegativeButton(R.string.update_skip_button, (dialog, which) ->
                    skipAppUpdate(newVersion));

            updateDialogBuilder.show();
        } catch (Exception ignored) {} // This is not critical, and may fail if the launcher window isn't visible
    }

    /**
     * Checks if an update to the main app has previously been identified by checking the DataStore.
     * Shows updates even if skipped.
     * @return True if an update is available
     */
    public static boolean isAppUpdateAvailible() {
        return updateAvailable;
    }

    /**
     * Checks for a main app update, and prompts the user if one is found
     */
    public void checkAppUpdateInteractive() {
        checkAppLatestVersion(this::storeLatestVersionAndPromptUpdate);
    }

    /**
     * Checks for a main app update, and installs it
     */
    public void checkAppUpdateAndInstall() {
        checkAppLatestVersion(this::updateApp);
    }

    public String getInstalledVersion() {
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(
                    getAppPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return packageInfo.versionName;
    }
    public int getInstalledVersionCode() {
        PackageInfo packageInfo;
        try {
            packageInfo = packageManager.getPackageInfo(
                    getAppPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
        return packageInfo.versionCode;
    }

    /**
     * Stores the latest app version to the datastore and prompts the user to update
     * @param newVersionName Version string of the version to update to
     */
    private void storeLatestVersionAndPromptUpdate(String newVersionName) {
        final String installedVersion = getInstalledVersion();
        if (!Objects.equals(newVersionName, installedVersion)) {
            updateAvailable = false;
            if (newVersionName.equals(getIgnoredUpdateTag())) return;
            Log.v(TAG, "New version available!");
            showAppUpdateDialog(installedVersion, newVersionName);
        } else {
            Log.i(TAG, getAppPackageName()+ " is up to date");
            updateAvailable = false;
            // Clear downloaded APKs
            FileLib.delete(activity.getExternalCacheDir()+"/"+APK_FOLDER);
        }
    }

    /**
     * Updates the app, right here, right now
     * @param newVersionName Version string of the version to update to
     */
    public void updateApp(String newVersionName) {
        final String installedVersion = getInstalledVersion();
        if (!Objects.equals(newVersionName, installedVersion)) {
            Log.i(TAG, "Installing version "+newVersionName+" of "+getAppPackageName());
            downloadPackage(getAppPackage(newVersionName));
        }
    }

    /**
     * Checks the latest version of the app
     * @param callback Called asynchronously with the latest version of the app
     */
    public void checkAppLatestVersion(Response.Listener<String> callback) {
        StringRequest updateRequest = new StringRequest(
                Request.Method.GET, String.format(URL_GITHUB_API_TEMPLATE, getGitRepo()),
                (response -> handleUpdateResponse(response, callback)),
                ((error) -> Log.w(TAG, "Couldn't get update info", error)));
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


    /**
     * Skips a specific main app update. An update prompt will not be shown for the skipped version.
     * @param versionTag Github release tag of the update to skip
     */
    public void skipAppUpdate(String versionTag) {
        AlertDialog.Builder skipDialogBuilder =
                new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        skipDialogBuilder.setTitle(activity.getString(R.string.update_skip_title, versionTag));
        skipDialogBuilder.setMessage(R.string.update_skip_content);
        skipDialogBuilder.setPositiveButton(R.string.update_skip_confirm_button, (dialog, i) -> {
            putIgnoredUpdateTag(versionTag);
            BasicDialog.toast(activity.getString(R.string.update_skip_toast), versionTag, false);
            dialog.dismiss();
        });
        skipDialogBuilder.setNegativeButton(R.string.update_skip_cancel_button, ((dialog, i) -> dialog.dismiss()));
        skipDialogBuilder.show();
    }

    /**
     * Stores the ignored update version tag
     */
    protected abstract void putIgnoredUpdateTag(String ignoredUpdateTag);

    /**
     * Gets the ignored update version tag (must be persistent!)
     */
    protected abstract String getIgnoredUpdateTag();
}
