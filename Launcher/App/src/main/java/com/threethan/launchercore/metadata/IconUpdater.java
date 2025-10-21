package com.threethan.launchercore.metadata;

import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;

import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.Platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * This abstract class is dedicated to downloading icons from online repositories
 * for apps and added websites.
 * <p>
 * Its functions are called by the Icon class. If no downloadable icon is found,
 * the Icon class will then decide on the icon to be used.
 */
public abstract class IconUpdater {
    // Special repository urls which take priority
    private static final String[] PRIORITY_URLS_SQUARE = {
            "https://raw.githubusercontent.com/threethan/QuestLauncherImages/main/icon/%s.jpg",
    };
    private static final String[] PRIORITY_URLS_BANNER = {
            "https://raw.githubusercontent.com/threethan/QuestLauncherImages/main/banner/%s.jpg",
    };
    // Type of image to retrieve from https://github.com/threethan/MetaMetadata
    private static final String IMAGE_TYPE_SQUARE = "icon";
    private static final String IMAGE_TYPE_BANNER = "landscape";

    // Repository URLs are used if metadata fails to return an icon
    // Each URL will be tried in order: the first with a file matching the package name will be used
    private static final String[] FALLBACK_URLS_SQUARE = {
            "https://raw.githubusercontent.com/veticia/binaries/main/icons/%s.png",
            "https://files.cocaine.trade/LauncherIcons/oculus_landscape/%s.png"
    };
    private static final String[] FALLBACK_URLS_BANNER = {
            "https://raw.githubusercontent.com/veticia/binaries/main/banners/%s.png",
            "https://files.cocaine.trade/LauncherIcons/oculus_icon/%s.png"
    };
    // Instead of matching a package name, websites match their TLD
    private static final String[] ICON_URLS_WEB = {
            "https://www.google.com/s2/favicons?domain=%s&sz=256", // Provides high-res icons
            "https://%s/favicon.ico", // The standard directory for a website's icon to be placed
    };
    private static final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * Stores the time when we're next allowed to try to download an icon for a package.
     * <p>
     * Downloads are NOT automatically called at this time, but will be called asynchronously
     * when the icon is next checked/displayed.
     * <p>
     * Since this isn't stored persistently, all icons will be rechecked when the app is fully quit.
     * This is a non-issue on LL since the LauncherService stays open persistently.
     */
    public static final ConcurrentHashMap<String, Long> nextCheckByPackageMs = new ConcurrentHashMap<>();

    // How many minutes before we can recheck/redownload an icon
    private static final long ICON_CHECK_TIME_MINUTES = 3;
    public static final ExecutorService executorService = Core.EXECUTOR;

    /**
     * Check if an icon should be downloaded for a particular app
     * @param app Application info of the app (currently only requires packageName)
     * @return True if the icon should be downloaded
     */
    private static synchronized boolean shouldDownload(ApplicationInfo app) {
        if (IconLoader.iconCustomFileForApp(app).exists()) return false;
        String packageName = App.isWebsite(app.packageName) ?
                StringLib.baseUrl(app.packageName) : app.packageName;
        if (!nextCheckByPackageMs.containsKey(packageName)) return true;
        final long nextCheckMs = Objects.requireNonNull(nextCheckByPackageMs.get(packageName));
        return System.currentTimeMillis() > nextCheckMs;
    }

    /**
     * Starts the download of an icon, if one should be downloaded for that app
     * @param app App for which to download an icon image
     * @param callback Called when the download completes successfully and the icon is changed
     */
    public static void check(ApplicationInfo app, final Consumer<Drawable> callback) {
        executorService.submit(() -> {
            if (!shouldDownload(app)) return;

            final boolean isWebsite = App.isWebsite(app.packageName);
            final String packageName = isWebsite ? StringLib.baseUrl(app.packageName) : app.packageName;

            final int delayMs = (int) (ICON_CHECK_TIME_MINUTES *1000*60);
            nextCheckByPackageMs.put(packageName, System.currentTimeMillis() + delayMs);

            final boolean isBanner = App.isBanner(app);
            final File iconFile = IconLoader.iconCacheFileForApp(app);

            Object lock = locks.putIfAbsent(packageName, new Object());
            if (lock == null) lock = locks.get(packageName);
            synchronized (Objects.requireNonNull(lock)) {
                try {
                    final String dlPkg = getDownloadString(app);

                    Resources res = Core.context().getResources();

                    // Priority repos
                    for (final String url : (isBanner ? PRIORITY_URLS_BANNER : PRIORITY_URLS_SQUARE)) {
                        Bitmap b = downloadIconFromUrl(String.format(url, dlPkg), iconFile);
                        if (b != null) {
                            callback.accept(new BitmapDrawable(res, b));
                            return;
                        }
                    }

                    // New Metadata method
                    if (Platform.isVr()) {
                        final MetaMetadata.App appMeta = MetaMetadata.getForPackage(dlPkg);
                        if (appMeta != null) {
                            Bitmap b = appMeta.downloadImage(
                                    isBanner ? IMAGE_TYPE_BANNER : IMAGE_TYPE_SQUARE, iconFile);
                            if (b != null) {
                                callback.accept(new BitmapDrawable(res, b));
                                return;
                            }
                        }
                    }

                    // Fallback repos
                    for (final String url : ( isWebsite ? ICON_URLS_WEB :
                            (isBanner ? FALLBACK_URLS_BANNER : FALLBACK_URLS_SQUARE))) {
                        Bitmap b = downloadIconFromUrl(String.format(url, dlPkg), iconFile);
                        if (b != null) {
                            callback.accept(new BitmapDrawable(res, b));
                            return;
                        }
                    }
                } catch (Exception e) {
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                } finally {
                    // Set the icon to now download if we either successfully downloaded it,
                    // or the download tried and failed
                    locks.remove(packageName);
                }
            }
        });
    }


    /**
     * Gets the package name as it should be used for download purposes,
     * may exclude parts of the package name used for app mods or variants
     * which should use the base app's icon
     * @param app ApplicationInfo of the app
     * @return PackageID of the app, optionally modified in some way
     */
    private static String getDownloadString(ApplicationInfo app) {
        if (App.isWebsite(app.packageName)) return StringLib.baseUrl(app.packageName);
        return app.packageName.replace(".mrf.", ".").replace("://", "");
    }

    /**
     * Downloads an icon from a given url and saves it using saveStream()
     * @return True if icon was downloaded and saved successfully
     */
    static @Nullable Bitmap downloadIconFromUrl(String url, File iconFile) {
        java.net.HttpURLConnection connection = null;
        try {
            android.net.TrafficStats.setThreadStatsTag(1);
            connection = (java.net.HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(500);
            connection.setReadTimeout(10000);

            Bitmap bitmap;
            try (InputStream inputStreamWithTimeout = connection.getInputStream()) {
                bitmap = saveStream(inputStreamWithTimeout, iconFile);
            }
            return bitmap;
        } catch (IOException ignored) {} finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
    /**
     * Saves an input stream used to download a bitmap to an actual file, applying webp compression.
     * @return A bitmap if the stream was different and has been saved
     */
    private static @Nullable Bitmap saveStream(InputStream inputStream, File outputFile) {
        try {
            final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null || bitmap.getHeight() < 10) {
                return null;
            }
            IconLoader.compressAndSaveBitmap(outputFile, bitmap);
            return bitmap;
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return null;
        }
    }
}