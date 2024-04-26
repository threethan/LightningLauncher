package com.threethan.launcher.helper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.lib.ImageLib;
import com.threethan.launcher.lib.StringLib;
import com.threethan.launcher.updater.IconUpdater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/*
    Icon

    This abstract class provides helper functions for getting, setting and saving icons.
    It uses IconRepo in order to request icons from the internet, if applicable
 */

public abstract class Icon {
    private static final int ICON_MAX_HEIGHT = 256;
    private static final int ICON_QUALITY = 90;
    public static final String ICON_CACHE_FOLDER = "/icon-cache";
    public static final String ICON_CUSTOM_FOLDER = "/icon-custom";
    public static final Map<String, Drawable> cachedIcons = new ConcurrentHashMap<>();
    private static final String BANNER_SUFFIX = "$banner";

    public static File iconCacheFileForPackage(ApplicationInfo app) {
        return iconFileForPackage(app, false);
    }
    public static File iconCustomFileForApp(ApplicationInfo app) {
        return iconFileForPackage(app, true);
    }
    private static File iconFileForPackage(ApplicationInfo app, boolean custom) {
        String cacheName = cacheName(app);
        ApplicationInfo tempApp = new ApplicationInfo();
        tempApp.packageName = cacheName;
        final boolean wide = App.isBanner(tempApp);
        final boolean oneIcon = custom || App.isWebsite(tempApp) || App.isShortcut(tempApp);
        return new File(LauncherActivity.getForegroundInstance().getApplicationInfo().dataDir +
                (custom ? ICON_CUSTOM_FOLDER : ICON_CACHE_FOLDER),
                cacheName + (wide && !oneIcon ? "-wide" : "") + ".webp");
    }
    public static void init() {
        // Icon init
        File cacheDir = new File(LauncherActivity.getForegroundInstance().getApplicationInfo().dataDir + Icon.ICON_CACHE_FOLDER);
        boolean ignored1 = cacheDir.mkdir();
        File customDir = new File(LauncherActivity.getForegroundInstance().getApplicationInfo().dataDir + Icon.ICON_CUSTOM_FOLDER);
        boolean ignored2 = customDir.mkdir();
    }

    public static String cacheName(ApplicationInfo app) {
        String cacheName;
        if (App.isWebsite(app))
            return StringLib.toValidFilename(StringLib.baseUrlWithScheme(app.packageName));
        else {
            cacheName = app.packageName;
            if (App.isBanner(app)) cacheName += BANNER_SUFFIX;
        }
        return StringLib.toValidFilename(cacheName);
    }
    public static void saveIcon(ApplicationInfo app, File iconFile) {
        try {
            Drawable newIconDrawable = Drawable.createFromPath(iconFile.getAbsolutePath());
            if (newIconDrawable != null) {
                cachedIcons.put(cacheName(app), newIconDrawable); // Success
            }
        } catch (Exception ignored) {
            Log.w("Icon", "Error when loading icon drawable from path "+iconFile.getAbsolutePath());
        }
    }
    public static void cacheIcon(ApplicationInfo app, Drawable iconDrawable) {
        cachedIcons.put(cacheName(app), iconDrawable);
    }
    @SuppressLint("UseCompatLoadingForDrawables")
    @Nullable
    public static Drawable loadIcon(LauncherActivity activity, ApplicationInfo app, ImageView imageView) {
        new LoadIconExecutor(activity, app, imageView).execute();
        if (Icon.cachedIcons.containsKey(Icon.cacheName(app))) return Icon.cachedIcons.get(Icon.cacheName(app));
        else return null;
    }

    public static void reloadIcon(LauncherActivity activity, ApplicationInfo app, ImageView downloadImageView) {
        final boolean ignored0 = iconCustomFileForApp(app).delete();
        final File iconFile = iconCacheFileForPackage(app);
        final boolean ignored1 = iconFile.delete();
        downloadImageView.setImageDrawable(loadIcon(activity, app, downloadImageView));
        IconUpdater.download(activity, app, null);
        BasicDialog.toast(activity.getString(R.string.refreshed_icon));
    }
    public static void saveIconDrawableExternal(Activity activity, Drawable icon, ApplicationInfo app) {
        try {
            Bitmap bitmap = ImageLib.bitmapFromDrawable(icon);
            if (bitmap == null)
                Log.i("Icon", "Failed to load drawable bitmap for "+app.packageName);
            else {
                String cacheName = cacheName(app);
                File f1 =  new File(activity.getApplicationInfo().dataDir + ICON_CUSTOM_FOLDER,
                        cacheName + ".webp");
                File f2 =  new File(activity.getApplicationInfo().dataDir + ICON_CUSTOM_FOLDER,
                        cacheName + BANNER_SUFFIX + ".webp");
                compressAndSaveBitmap(f1,bitmap);
                compressAndSaveBitmap(f2,bitmap);
            }
        } catch (Exception e) {
            Log.i("ICON", "Exception while converting file " + app.packageName);
            e.printStackTrace();
        }

    }
    protected static Bitmap scaleBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float aspectRatioInv = (float) height / width;
        if (height > ICON_MAX_HEIGHT) {
            height = ICON_MAX_HEIGHT;
            width = Math.round(height / aspectRatioInv);
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        return bitmap;
    }
    public static void compressAndSaveBitmap(File file, Bitmap bitmap) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Objects.requireNonNull(file.getParentFile()).mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath(), false);
            bitmap = scaleBitmap(bitmap);
            bitmap.compress(Bitmap.CompressFormat.WEBP, ICON_QUALITY, fileOutputStream);
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Loads an icon asynchronously
     */
    protected static class LoadIconExecutor {
        private final LauncherActivity activity;
        private final ApplicationInfo app;
        private final ImageView imageView;

        protected LoadIconExecutor(LauncherActivity activity, ApplicationInfo app, ImageView imageView) {
            this.activity = activity;
            this.app = app;
            this.imageView = imageView;
        }

        public void execute() {
            Thread thread = new Thread(() -> {
                Drawable appIcon = loadIcon();
                if (appIcon != null) {
                    Icon.cacheIcon(app, appIcon);
                    if (imageView != null) activity.runOnUiThread(() -> imageView.setImageDrawable(appIcon));
                }
            });
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        @SuppressLint("UseCompatLoadingForDrawables")
        @Nullable
        public Drawable loadIcon() {
            Drawable appIcon = null;

            // Try to load from custom icon file
            final File iconCustomFile = Icon.iconCustomFileForApp(app);
            if (iconCustomFile.exists()) appIcon = Drawable.createFromPath(iconCustomFile.getAbsolutePath());
            if (appIcon != null) return appIcon;

            // Everything in the try will still attempt to download an icon
            try {
                // Try to load from cached icon file
                final File iconCacheFile = Icon.iconCacheFileForPackage(app);
                if (iconCacheFile.exists()) appIcon = Drawable.createFromPath(iconCacheFile.getAbsolutePath());
                if (appIcon != null) return appIcon;

                // Try to load from package manager
                PackageManager packageManager = activity.getPackageManager();
                Resources resources = packageManager.getResourcesForApplication(app);

                // Check Icon
                int iconId = app.icon;
                // Check AndroidTV banner
                if (app.banner != 0 && App.isBanner(app)) iconId = app.banner;

                if (iconId == 0) iconId = android.R.drawable.sym_def_app_icon;
                appIcon = resources.getDrawable(iconId, null);

                return appIcon;
            } catch (PackageManager.NameNotFoundException ignored) { // Fails on websites
                return null;
            }  finally {
                // Attempt to download the icon for this app from an online repo
                // Done AFTER saving the drawable version to prevent a race condition)
                IconUpdater.check(activity, app, () ->
                        activity.launcherService.forEachActivity(a ->
                                Objects.requireNonNull(a.getAppAdapter()).notifyItemChanged(app)));
            }
        }
    }
}
