package com.threethan.launcher.helper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.launcher.LauncherActivity;
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
        return new File(LauncherActivity.getAnyInstance().getApplicationInfo().dataDir +
                (custom ? ICON_CUSTOM_FOLDER : ICON_CACHE_FOLDER),
                cacheName + (wide && !oneIcon ? "-wide" : "") + ".webp");
    }
    public static void init() {
        // Icon init
        File cacheDir = new File(LauncherActivity.getAnyInstance().getApplicationInfo().dataDir + Icon.ICON_CACHE_FOLDER);
        boolean ignored1 = cacheDir.mkdir();
        File customDir = new File(LauncherActivity.getAnyInstance().getApplicationInfo().dataDir + Icon.ICON_CUSTOM_FOLDER);
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
        IconExecutor.execute(activity, app, imageView);
        if (Icon.cachedIcons.containsKey(Icon.cacheName(app))) return Icon.cachedIcons.get(Icon.cacheName(app));
        else return null;
    }

    public static void reloadIcon(LauncherActivity activity, ApplicationInfo app, ImageView downloadImageView) {
        final boolean ignored0 = iconCustomFileForApp(app).delete();
        final File iconFile = iconCacheFileForPackage(app);
        final boolean ignored1 = iconFile.delete();
        downloadImageView.setImageDrawable(loadIcon(activity, app, downloadImageView));
        IconUpdater.download(activity, app, null);
        Dialog.toast(activity.getString(R.string.refreshed_icon));
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
}
