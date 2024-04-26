package com.threethan.launcher.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.threethan.launcher.R;
import com.threethan.launcher.lib.FileLib;

import java.io.File;
import java.util.Objects;

/**
 * Provides functionality for installing and updating addon packages
 */
public class RemotePackageUpdater {
    /**
     * The 'android:authority' of the provider which implements/has the name of
     *  "android.support.v4.content.FileProvider"
     *  (The package name is prepended automatically)
     */
    private static final String PROVIDER = /*packageName +*/".fileprovider";

    /**
     * Stores information for a package which may be downloaded using RemotePackageUpdater
     */
    public static class RemotePackage {
        public String packageName;
        public String url;
        public String latestVersion;

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof RemotePackage)
                return Objects.equals(packageName, ((RemotePackage) obj).packageName);
            else return false;
        }

        /**
         * Creates a new remotePackage
         * @param packageName Name of the package once installed
         * @param latestVersion Latest version (string) of the package
         * @param url String url from which to download the package
         */
        public RemotePackage(String packageName, String latestVersion, String url) {
            this.packageName = packageName;
            this.latestVersion = latestVersion;
            this.url = url;
        }
        @NonNull
        @Override
        public String toString() {
            String[] split = this.packageName.split("\\.");
            return split[split.length-1];
        }
    }

    /**
     * Tag for console messages
     */
    protected static final String TAG = "Remote Package Updater";

    /**
     * Temp directory for downloaded apks in external cache dir., may be cleared unexpectedly
     */
    public static final String APK_FOLDER = "downloadedApk";
    protected final PackageManager packageManager;
    protected final Activity activity;

    /**
     * Identifies possible installation states of a package.
     * If a RemotePackage is a service, INSTALLED_SERVICE_INACTIVE will be used if it is installed,
     * but does not yet have an active accessibility service
     */
    public enum AddonState
    { NOT_INSTALLED, INSTALLED_HAS_UPDATE, INSTALLED_SERVICE_INACTIVE, INSTALLED_SERVICE_ACTIVE, INSTALLED_APP }
    String latestVersionTag;
    private AlertDialog downloadingDialog;

    public RemotePackageUpdater(Activity activity) {
        this.activity = activity;
        this.packageManager = activity.getPackageManager();
    }

    /**
     * Downloads the package, then prompts the user to install it
     * @param remotePackage RemotePackage to download
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Can't be fixed on this API version
    protected void downloadPackage(RemotePackage remotePackage) {
        Log.v(TAG, "Downloading from url "+remotePackage.url);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(remotePackage.url));
        request.setDescription("Downloading "+remotePackage); // Notification
        request.setTitle("Lightning Launcher Auto-Updater");

        final String apkFileName = remotePackage+remotePackage.latestVersion+".apk";
        final File apkFile = new File(activity.getExternalCacheDir()+"/"+APK_FOLDER, apkFileName);

        FileLib.delete(apkFile.getParent());

        request.setDestinationUri(Uri.fromFile(apkFile));
        DownloadManager manager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadingDialog == null) {
            try {
                AlertDialog.Builder updateDialogBuilder = new AlertDialog.Builder(activity,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert);
                updateDialogBuilder.setTitle(activity.getString(R.string.update_downloading_title, remotePackage));
                updateDialogBuilder.setMessage(R.string.update_downloading_content);
                updateDialogBuilder.setNegativeButton(R.string.update_hide_button, (dialog, which) -> dialog.cancel());
                updateDialogBuilder.setOnDismissListener(d -> downloadingDialog = null);
                downloadingDialog = updateDialogBuilder.show();
            } catch (Exception ignored) {} // May rarely fail if window is invalid
        }


        // Registers a one-off reciever to install the downloaded package
        // Android will prompt the user if they actually want to install
        activity.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (downloadingDialog != null) downloadingDialog.dismiss();
                installApk(apkFile);
                activity.unregisterReceiver(this);
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // Start the download
        manager.enqueue(request);
    }

    /**
     * Installs an apk from a file. May be called externally.
     * <p>
     * A message will be shown if the file does not exist.
     * @param apkFile File pointing to the apk
     */
    public void installApk(File apkFile) {
        Log.v(TAG, "Installing from apk at "+apkFile.getAbsolutePath());
        if (apkFile.exists()) {
            Uri apkURI = FileProvider.getUriForFile(activity,
                    activity.getApplicationContext().getPackageName() + PROVIDER,
                    apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

            if (downloadingDialog != null) downloadingDialog.dismiss();
            activity.startActivity(intent);
        } else {
            try {
                AlertDialog.Builder dialog = new AlertDialog.Builder(activity,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert);
                dialog.setTitle(R.string.update_failed_title);
                dialog.setMessage(R.string.update_failed_content);
                dialog.setNegativeButton(R.string.update_hide_button, (d, w) -> d.cancel());
                dialog.show();
            } catch (Exception ignored) {}
        }
    }
}