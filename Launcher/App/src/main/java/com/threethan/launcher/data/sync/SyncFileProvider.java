package com.threethan.launcher.data.sync;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Syncs preference data with another app instance.
 * This implementation allows for exactly two apps, and no more.
 */
public class SyncFileProvider extends FileProvider {
    private static final String PROVIDER_NAME = ".syncFileProvider";

    /**
     * List of files to sync between apps
     */
    private static List<File> getFilesToSync() {
        return List.of(
                SyncCoordinator.getRelativeFileFor(SyncCoordinator.DATA_STORE_DEFAULT),
                SyncCoordinator.getRelativeFileFor(SyncCoordinator.DATA_STORE_SORT),
                SyncCoordinator.getRelativeFileFor(SyncCoordinator.DATA_STORE_PER_APP)
        );
    }

    /**
     * Gets a content URI for a file from the provider
     * @param fromPackageName Package providing the file
     * @param file File relative path reference
     * @return File URI
     */
    private static Uri getUri(String fromPackageName, File file) {
        if (file.getPath().endsWith("/") || file.getPath().startsWith("/"))
            throw new IllegalArgumentException("Relative path start or end with '/'");
        return Uri.parse("content://" + fromPackageName + PROVIDER_NAME + "/" + file.getPath());
    }

    /**
     * Grants another app access to this app's config files
     */
    public static void grantAccessToTarget(Context context, String targetPackageName) {
        String sourcePackage = context.getPackageName();
        //noinspection SimplifyStreamApiCallChains
        List<Uri> uris = getFilesToSync().stream().map(f -> getUri(sourcePackage, f))
                .collect(java.util.stream.Collectors.toList());
        for (Uri uri : uris) {
            context.grantUriPermission(targetPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.i("DataStoreSync", "Granted access to " + uri + " for " + targetPackageName);
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
     * Copies sync data from the other app
     */
    public static void copyDataFromOtherApp(Context context, String masterPackageName) {
        try {
            for (File file : getFilesToSync())
                copyFileFromOtherApp(context, file, masterPackageName);
            Log.i("DataStoreSync", "Copied datastore from " + masterPackageName);
        } catch (Exception e) {
            Log.e("DataStoreSync", "Failed to copy datastore from " + masterPackageName, e);
        }
    }
}
