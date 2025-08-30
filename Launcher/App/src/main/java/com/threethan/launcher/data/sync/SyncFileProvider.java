package com.threethan.launcher.data.sync;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.threethan.launcher.helper.SettingsSaver;
import com.threethan.launchercore.util.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Syncs preference data with another app instance.
 * This implementation allows for exactly two apps, and no more.
 */
public class SyncFileProvider extends FileProvider {
    private static final String PROVIDER_NAME = ".syncFileProvider";

    /**
     *  List of supported package names.
     *  The Google Play build target is intentionally excluded.
     */
    private static final List<String> SUPPORTED_PACKAGES = List.of(
            "com.threethan.launcher",
            "com.threethan.launcher.metastore"
    );

    private static final File FILE_MAIN = new File("datastore/" + SettingsSaver.DATA_STORE_MAIN);
    private static final File FILE_SORT = new File("datastore/" + SettingsSaver.DATA_STORE_SORT);

    /**
     * List of files to be synced
     */
    private static final List<File> FILES_TO_SYNC = List.of(FILE_MAIN, FILE_SORT);

    /**
     * Gets a content URI for a file from the provider
     * @param fromPackageName Package providing the file
     * @param file File relative path reference
     * @return File URI
     */
    private static Uri getUri(String fromPackageName, File file) {
        return Uri.parse("content://" + fromPackageName + PROVIDER_NAME + "/" + file.getPath());
    }

    /**
     * Grants another app access to this app's config files
     */
    public static void grantAccessToOtherApp(Context context) {
        String pn = context.getPackageName();
        String otherAppPackageName = getOtherPackageName(context);
        //noinspection SimplifyStreamApiCallChains
        List<Uri> uris = FILES_TO_SYNC.stream().map(f -> getUri(pn, f))
                .collect(java.util.stream.Collectors.toList());
        for (Uri uri : uris) {
            context.grantUriPermission(otherAppPackageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Log.i("DataStoreSync", "Granted access to " + uri + " for " + otherAppPackageName);
        }
    }

    /**
     * Copies a given file from another app
     * @throws Exception If permission is not granted
     */
    public static void copyFileFromOtherApp(Context context, File file, String otherAppPackageName) throws Exception {
        File destFile = new File(context.getFilesDir(), file.getPath());
        File tempFile = new File(destFile.getAbsolutePath() + "_temp");

        Uri sourceUri = getUri(otherAppPackageName, file);
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in != null ? in.read(buffer) : 0) > 0) out.write(buffer, 0, len);
        }

        // Copy and replace
        if (destFile.exists()) {
            boolean ignored = destFile.delete();
        }
        boolean ignored = tempFile.renameTo(destFile);
    }

    /**
     * Copies a given file to another app
     * @throws Exception If permission is not granted
     */
    public static void copyFileToOtherApp(Context context, File file, String otherAppPackageName) throws Exception {
        File sourceFile = new File(context.getFilesDir(), file.getPath());
        Uri destUri = getUri(otherAppPackageName, file);
        try (InputStream in = new java.io.FileInputStream(sourceFile);
             OutputStream out = context.getContentResolver().openOutputStream(destUri)) {
            byte[] buffer = new byte[4096];
            int len;
            while (out != null && (len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        }
    }

    /**
     * Copies sync data from the other app
     */
    public static void copyDataFromOtherApp(Context context) {
        String masterPackageName = getOtherPackageName(context);

        if (masterPackageName != null)
            if (!sendIntent(context, masterPackageName, true)) return;
        for (String otherAppPackageName : SUPPORTED_PACKAGES) {
            if (otherAppPackageName.equals(context.getPackageName())) continue;
            if (otherAppPackageName.equals(masterPackageName)) continue;
            sendIntent(context, otherAppPackageName, false);
        }
        if (masterPackageName != null) {
            for (int i = 0; i < 250; i++) {
                try {
                    for (File file : FILES_TO_SYNC)
                        copyFileFromOtherApp(context, file, masterPackageName);
                    Log.i("DataStoreSync", "Copied datastore from " + masterPackageName);
                    break;
                } catch (Exception ignored) {
                    // Wait a tiny bit if failed
                    try { Thread.sleep(10);} catch (Exception ignored1) {}
                }
            }
        }
    }

    /**
     * Send the intent to kill the other app's activity,
     * and - optionally - request access to its settings
     */
    private static boolean sendIntent(Context context, String packageName, boolean grant) {
        Intent intent = new Intent("com.threethan.launcher.GRANT_URI_RECEIVER");
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra("grant", grant);
        if (context.getPackageManager().queryBroadcastReceivers(intent, 0).isEmpty()) return false;
        context.sendBroadcast(intent);
        return true;
    }

    /**
     *  Get the package name of the app to sync with.
     */
    private static @Nullable String getOtherPackageName(Context context) {
        List<String> otherPackages = new ArrayList<>(SUPPORTED_PACKAGES);
        otherPackages.removeIf(n -> !App.packageExists(n) || n.equals(context.getPackageName()));
        if (otherPackages.size() > 1) {
            Log.e("DataStoreSync", "[CRITICAL] Too many packages installed! Sync will be suspended!");
            return null;
        } else if (otherPackages.size() == 1) {
            return otherPackages.get(0);
        }
        return null;
    }

    /**
     *  Copies this app's data to other supported apps
     */
    private static void copyDataToOtherApps(Context context) {
        String masterPackageName = getOtherPackageName(context);
        try {
            for (File file : FILES_TO_SYNC)
                copyFileToOtherApp(context, file, masterPackageName);
        } catch (Exception ignored) {}
    }

    /** Call to perform sync when the app is started */
    public static void onStart(Activity activity) {
        copyDataFromOtherApp(activity);
    }

    /** Call to propagate changed settings to other apps in case of an uninstall */
    public static void onStop(Activity activity) {
        copyDataToOtherApps(activity);
    }
}
