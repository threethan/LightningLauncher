package com.threethan.launcher.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launchercore.lib.FileLib;
import com.threethan.launchercore.util.CustomDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        public final String packageName;
        public final String url;
        public final String latestVersion;

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
     * @noinspection unused
     */
    public enum AddonState
    { NOT_INSTALLED, INSTALLED_HAS_UPDATE, INSTALLED_SERVICE_INACTIVE, INSTALLED_SERVICE_ACTIVE, INSTALLED_APP }

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
    public void downloadPackage(RemotePackage remotePackage) {
        if (!BasicDialog.validateVariantWithNotify()) return;

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
                downloadingDialog = new CustomDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.update_downloading_title, remotePackage))
                    .setMessage(R.string.update_downloading_content)
                    .setNegativeButton(R.string.update_hide_button, (dialog, which) -> dialog.cancel())
                    .setOnDismissListener(d -> downloadingDialog = null)
                    .show();
            } catch (Exception ignored) {} // May rarely fail if window is invalid
        }


        // Registers a one-off receiver to install the downloaded package
        // Android will prompt the user if they actually want to install
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (downloadingDialog != null) downloadingDialog.dismiss();
                installApk(apkFile);
                activity.unregisterReceiver(this);
            }
        };

        activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

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
        if (!BasicDialog.validateVariantWithNotify()) return;

        Log.v(TAG, "Installing from apk at " + apkFile.getAbsolutePath());
        if (apkFile.exists()) {
            Uri apkURI = FileProvider.getUriForFile(activity,
                    activity.getApplicationContext().getPackageName() + PROVIDER,
                    apkFile);
            installApk(apkURI);
        } else {
            try {
                new CustomDialog.Builder(activity)
                        .setTitle(R.string.update_failed_title)
                        .setMessage(R.string.update_failed_content)
                        .setNegativeButton(R.string.update_hide_button, (d, w) -> d.cancel())
                        .show();
            } catch (Exception ignored) {}
        }

}
    @SuppressLint("RequestInstallPackagesPolicy")
    public void installApk(Uri apkURI) {
        if (!BasicDialog.validateVariantWithNotify()) return;

        Runnable viewApk = () -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

            if (downloadingDialog != null) downloadingDialog.dismiss();
            activity.startActivity(intent);
        };

        try {
            // Session-based install
            PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = packageInstaller.createSession(params);

            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            try (
                 InputStream in = activity.getContentResolver().openInputStream(apkURI);
                 OutputStream out = session.openWrite("package", 0, -1)) {
                byte[] buffer = new byte[65536];
                int c;
                //noinspection DataFlowIssue
                while ((c = in.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                }
                session.fsync(out);


            } catch (IOException e) {
                session.abandon();
                throw e;
            }

            BasicDialog.toast(activity.getString(R.string.installing));
            InstallReceiver.setOnSuccess(() ->
                    BasicDialog.toast(activity.getString(R.string.installed_successfully)));

            session.commit(createIntentSender(activity, sessionId));

            session.close();

            Log.i(TAG, "Session-based install finished for " + apkURI);

        } catch (Throwable e) {

            Log.w(TAG, "Session-based install failed, fallback to view intent", e);

            viewApk.run();
        }
    }

    private static IntentSender createIntentSender(Context context, int sessionId) {
        Intent intent = new Intent(context, InstallReceiver.class);
        intent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        return PendingIntent.getBroadcast(context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                .getIntentSender();
    }

}