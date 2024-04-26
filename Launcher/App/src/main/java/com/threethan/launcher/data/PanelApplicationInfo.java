package com.threethan.launcher.data;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This class is used to create applicationinfo for panel apps,
 * which often do not actually have corresponding packages
 */
public class PanelApplicationInfo extends ApplicationInfo {
    public static final String packagePrefix = "panel:";
    String label;
    public PanelApplicationInfo(String label, String uri) {
        if (!uri.contains("//")) uri = packagePrefix + uri;
        this.packageName = uri;
        this.label = label;
    }
    @NonNull
    @Override
    public CharSequence loadLabel(@NonNull PackageManager pm) {
        return label;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        ApplicationInfo castObj = obj instanceof ApplicationInfo ? ((ApplicationInfo) obj) : null;
        if (castObj != null && castObj.packageName.equals(packageName)) return true;
        else return super.equals(obj);
    }
}