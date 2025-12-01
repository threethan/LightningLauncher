package com.threethan.launchercore.metadata;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import com.threethan.launcher.R;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.adapter.UtilityApplicationInfo;
import com.threethan.launchercore.lib.ImageLib;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/*
    Icon

    This abstract class provides helper functions for getting, setting and saving icons.
    It uses IconRepo in order to request icons from the internet, if applicable
 */

public abstract class IconLoader {
    public static final int SAVED_ICON_HEIGHT = 256;
    public static final int SAVED_ICON_QUALITY = 25;
    public static final String ICON_CACHE_FOLDER = "/icon-cache";
    public static final Map<String, SoftReference<Drawable>> cachedIcons = new ConcurrentHashMap<>();
    public static final Object ICON_CUSTOM_FOLDER = "/icon-custom";

    private static final Drawable FALLBACK_DRAWABLE = null;

    /**
     * Loads the icon for an app.
     * The callback will be called immediately on this thread,
     * and may also be called on a different thread after a small delay
     * @param app App to get the icon for
     * @param consumer Consumer which handles the icon
     */
    public static void loadIcon(ApplicationInfo app, final Consumer<Drawable> consumer) {
        // Get synchronous icons for search urls
        if (app.packageName.startsWith("https://") && StringLib.isSearchUrl(app.packageName)) {
            if (app.packageName.startsWith("https://www.google.com/")) {
                consumer.accept(Core.context().getDrawable(R.drawable.ai_google));
                return;
            } else if (app.packageName.startsWith("https://www.youtube.com/")) {
                consumer.accept(Core.context().getDrawable(R.drawable.ai_youtube));
                return;
            } else if (app.packageName.startsWith("https://www.apkmirror.com/")) {
                consumer.accept(Core.context().getDrawable(R.drawable.ai_apkmirror));
                return;
            } else if (app.packageName.startsWith("https://play.google.com/")) {
                consumer.accept(Core.context().getDrawable(R.drawable.ai_playstore));
                return;
            }
        }

        if (app instanceof UtilityApplicationInfo uApp)
            consumer.accept(uApp.getDrawable());
        else {
            if (IconLoader.cachedIcons.containsKey(app.packageName)) {
                if (IconLoader.cachedIcons.get(app.packageName) != null) {
                    SoftReference<Drawable> cached = IconLoader.cachedIcons.get(app.packageName);
                    if (cached != null && cached.get() != null) {
                        consumer.accept(cached.get());
                        return;
                    }
                }
            }
            loadIcon(icon -> {
                consumer.accept(icon);
                if (icon != FALLBACK_DRAWABLE) {
                    SoftReference<Drawable> c1 = cachedIcons.getOrDefault(app.packageName, null);
                    Drawable cached = c1 == null ? null : c1.get();
                    if (cached instanceof BitmapDrawable cachedBitmap && icon instanceof BitmapDrawable iconBitmap) {
                        if (cachedBitmap.getBitmap().getHeight() > iconBitmap.getBitmap().getHeight())
                            return;
                    }
                    cachedIcons.put(
                            App.getType(app).equals(App.Type.WEB)
                                    ? StringLib.baseUrl(app.packageName)
                                    : app.packageName,
                            new SoftReference<>(icon));
                }
            }, app);
        }
    }

    private static void loadIcon(Consumer<Drawable> callback, ApplicationInfo app) {
        if (app instanceof UtilityApplicationInfo uApp) {
            callback.accept(uApp.getDrawable());
            return;
        }

        // Everything in the try will still attempt to download an icon
        Drawable appIcon = null;

        // Try to load from external custom icon file
        final File iconCustomFile = iconCustomFileForApp(app);
        if (iconCustomFile.exists())
            appIcon = Drawable.createFromPath(iconCustomFile.getAbsolutePath());
        if (appIcon != null) {
            callback.accept(appIcon);
            return;
        }

        // Try to load from cached icon file
        final File iconCacheFile = iconCacheFileForApp(app);

        if (iconCacheFile.exists())
            appIcon = Drawable.createFromPath(iconCacheFile.getAbsolutePath());
        if (appIcon != null) {
            callback.accept(appIcon);
            return;
        }
        // Try to load from package manager
        PackageManager packageManager = Core.context().getPackageManager();
        appIcon = app.banner != 0 && App.isBanner(app)
                ? packageManager.getApplicationBanner(app)
                : packageManager.getApplicationIcon(app);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && appIcon instanceof AdaptiveIconDrawable adaptiveIcon
                && adaptiveIcon.getIntrinsicWidth() == 0)
            appIcon = null;

        callback.accept(appIcon == null ? FALLBACK_DRAWABLE : appIcon);

        // Attempt to download the icon for this app from an online repo
        // Done AFTER saving the drawable version to prevent a race condition)
        IconUpdater.check(app, callback);
    }

    /** @return The file location which should be used for the applications cache file */
    public static File iconCacheFileForApp(ApplicationInfo app) {
        String cacheName = cacheName(app);
        final boolean banner = App.isBanner(app);
        return new File(Core.context().getApplicationInfo().dataDir + ICON_CACHE_FOLDER,
                cacheName + (banner ? "-banner" : "") + ".webp");
    }
    public static File iconCustomFileForApp(ApplicationInfo app) {
        String cacheName = cacheName(app);
        final boolean banner = App.isBanner(app);
        return new File(Core.context().getApplicationInfo().dataDir + ICON_CUSTOM_FOLDER,
                cacheName + (banner ? "-banner" : "") + ".webp");
    }
    public static String cacheName(ApplicationInfo app) {
        if (App.getType(app.packageName) == App.Type.WEB)
            return StringLib.toValidFilename(StringLib.baseUrl(app.packageName));
        return StringLib.toValidFilename(app.packageName);
    }
    public static void saveIconDrawableExternal(Drawable icon, ApplicationInfo app) {
        try {
            Bitmap bitmap = ImageLib.bitmapFromDrawable(icon);
            if (bitmap == null)
                Log.i("Icon", "Failed to load drawable bitmap for "+app.packageName);
            else {
                String cacheName = cacheName(app);
                File f1 =  new File(Core.context().getApplicationInfo().dataDir + ICON_CUSTOM_FOLDER,
                        cacheName + ".webp");
                File f2 =  new File(Core.context().getApplicationInfo().dataDir + ICON_CUSTOM_FOLDER,
                        cacheName + "-banner" + ".webp");
                compressAndSaveBitmap(f1,bitmap);
                compressAndSaveBitmap(f2,bitmap);
            }
        } catch (Exception e) {
            Log.i("Icon", "Exception while converting file " + app.packageName, e);
        }
    }
    public static void compressAndSaveBitmap(File file, Bitmap bitmap) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Objects.requireNonNull(file.getParentFile()).mkdirs();
            Bitmap resizedBitmap = ImageLib.getResizedBitmap(bitmap, SAVED_ICON_HEIGHT);
            try (FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath(), false)) {
                resizedBitmap.compress(Bitmap.CompressFormat.WEBP, SAVED_ICON_QUALITY, fileOutputStream);
            }
        } catch (IOException e) {
            Log.e("Icon", "IOException during bitmap save", e);
        }
    }
}
