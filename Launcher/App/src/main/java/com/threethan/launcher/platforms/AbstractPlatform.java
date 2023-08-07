package com.threethan.launcher.platforms;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractPlatform {

    protected static final HashMap<String, Drawable> cachedIcons = new HashMap<>();
    protected static final HashSet<String> excludedIconPackages = new HashSet<>();

    private static final String OCULUS_ICONS_URL = "https://raw.githubusercontent.com/basti564/LauncherIcons/main/oculus_square/";
    private static final String PICO_ICONS_URL = "https://raw.githubusercontent.com/basti564/LauncherIcons/main/pico_square/";
    private static final String VIVEPORT_ICONS_URL = "https://raw.githubusercontent.com/basti564/LauncherIcons/main/viveport_square/";
    private static final String VIVE_BUSINESS_ICONS_URL = "https://raw.githubusercontent.com/basti564/LauncherIcons/main/vive_business_square/";

    public static void clearIconCache() {
        excludedIconPackages.clear();
        cachedIcons.clear();
    }

    public static boolean isImageFileComplete(File imageFile) {
        boolean success = false;
        try {
            if (imageFile.length() > 0) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                success = (options.outWidth > 0 && options.outHeight > 0);
            }
        } catch (Exception e) {
            // Do nothing
        }

        if (!success) {
            Log.e("imgComplete", "Failed to validate image file: " + imageFile);
        }

        return success;
    }

    public static AbstractPlatform getPlatform(ApplicationInfo applicationInfo) {
        return new AppPlatform();
    }

    public static File packageToPath(Context context, String packageName) {
        return new File(context.getApplicationInfo().dataDir, packageName + ".webp");
    }

    public static boolean updateIcon(File file, String packageName) {
        try {
            Drawable newIconDrawable = Drawable.createFromPath(file.getAbsolutePath());
            if (newIconDrawable != null) {
                cachedIcons.put(packageName, newIconDrawable);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    protected static boolean saveStream(InputStream inputStream, File outputFile) {
        try {
            DataInputStream dataInputStream = new DataInputStream(inputStream);

            int length;
            byte[] buffer = new byte[65536];
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            while ((length = dataInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            fileOutputStream.flush();
            fileOutputStream.close();

            if (!isImageFileComplete(outputFile)) {
                return false;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float aspectRatio = (float) width / height;
                if (width > 512) {
                    width = 512;
                    height = Math.round(width / aspectRatio);
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                }

                try {
                    fileOutputStream = new FileOutputStream(outputFile);
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 90, fileOutputStream);
                    fileOutputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isHTCHeadset() {
        String manufacturer = Build.MANUFACTURER.toUpperCase();
        return manufacturer.startsWith("HTC");
    }

    public static boolean isMagicLeapHeadset() {
        String manufacturer = Build.MANUFACTURER.toUpperCase();
        return manufacturer.startsWith("MAGIC LEAP");
    }

    public static boolean isOculusHeadset() {
        String manufacturer = Build.MANUFACTURER.toUpperCase();
        return manufacturer.startsWith("META") || manufacturer.startsWith("OCULUS");
    }

    public static boolean isPicoHeadset() {
        String manufacturer = Build.MANUFACTURER.toUpperCase();
        return manufacturer.startsWith("PICO") || manufacturer.startsWith("PİCO"); // PİCO on turkish systems
    }

    public static boolean isVirtualRealityApp(ApplicationInfo applicationInfo) {
        if (applicationInfo.metaData != null) {
            for (String key : applicationInfo.metaData.keySet()) {
                if (key.contains("com.oculus.supportedDevices")) {
                    return true;
                }
                if (key.contains("vr.application.mode")) {
                    return true;
                }
                if (key.startsWith("notch.config")) {
                    return true;
                }
            }
        }
        return false;
    }

    public abstract boolean isSupported(Context context);

    public Drawable loadIcon(Activity activity, ApplicationInfo appInfo) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = activity.getPackageManager();
        Resources resources;

        resources = packageManager.getResourcesForApplication(appInfo.packageName);

        if (cachedIcons.containsKey(appInfo.packageName)) {
            return cachedIcons.get(appInfo.packageName);
        }

        int iconId = appInfo.icon;
        if (iconId == 0) {
            iconId = android.R.drawable.sym_def_app_icon;
        }

        Drawable appIcon = ResourcesCompat.getDrawableForDensity(resources, iconId, DisplayMetrics.DENSITY_XXXHIGH, null);

        final File iconFile = packageToPath(activity, appInfo.packageName);

        if (iconFile.exists()) {
            AbstractPlatform.updateIcon(iconFile, appInfo.packageName);
            return Drawable.createFromPath(iconFile.getAbsolutePath());
        }

        downloadIcon(activity, appInfo.packageName, () -> {
           updateIcon(iconFile, appInfo.packageName);
        });
        if (cachedIcons.containsKey(appInfo.packageName)) {
            return cachedIcons.get(appInfo.packageName);
        }
        return appIcon;
    }

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private void downloadIcon(final Activity activity, String pkgName, final Runnable callback) {
        final File iconFile = packageToPath(activity, pkgName);
        new Thread(() -> {
            Object lock = locks.putIfAbsent(pkgName, new Object());
            if (lock == null) {
                lock = locks.get(pkgName);
            }
            synchronized (Objects.requireNonNull(lock)) {
                try {
                    String url = OCULUS_ICONS_URL + pkgName + ".jpg";
                    if (downloadIconFromUrl(url, iconFile)) {
                        activity.runOnUiThread(callback);
                        return;
                    }
                    url = PICO_ICONS_URL + pkgName + ".png";
                    if (downloadIconFromUrl(url, iconFile)) {
                        activity.runOnUiThread(callback);
                        return;
                    }
                    url = VIVEPORT_ICONS_URL + pkgName + ".webp";
                    if (downloadIconFromUrl(url, iconFile)) {
                        activity.runOnUiThread(callback);
                        return;
                    }
                    url = VIVE_BUSINESS_ICONS_URL + pkgName + ".webp";
                    if (downloadIconFromUrl(url, iconFile)) {
                        activity.runOnUiThread(callback);
                        return;
                    }
//                    Log.i("downloadIcon", "Missing icon: " +iconFile.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    locks.remove(pkgName);
                }
            }
        }).start();
    }

    public abstract void runApp(Activity context, ApplicationInfo applicationInfo);

    boolean downloadIconFromUrl(String url, File iconFile) {
        try {
            try (InputStream inputStream = new URL(url).openStream()) {
                if (saveStream(inputStream, iconFile)) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
            Log.d("File not found", e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}