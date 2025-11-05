package com.threethan.launcher.helper;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;

public class AppBackgroundHelper {
    public static Drawable getAppBackgroundDrawable(Context context) {

        GradientDrawable bg = new GradientDrawable();

        bg.setColor(LauncherActivity.darkMode ? 0xFF333333 : 0xFFAAAAAA);

        if (knownIconCornerRadius < 0) {
            int radius = Compat.getDataStoreEditor().getInt(
                    LauncherActivity.layoutHorizontal ? Settings.KEY_ICON_CORNER_RADIUS_HORIZONTAL : Settings.KEY_ICON_CORNER_RADIUS_VERTICAL,
                    LauncherActivity.layoutHorizontal ? Settings.DEFAULT_ICON_CORNER_RADIUS_HORIZONTAL : Settings.DEFAULT_ICON_CORNER_RADIUS_VERTICAL);

            if (radius > 29) radius = 999;
            if (radius > 20) radius = 20 + (radius-20)*2;
            if (radius > 25) radius = 25 + (radius-25)*2;

            int scaling = Compat.getDataStoreEditor().getInt(
                    Settings.KEY_SCALE_VERTICAL,
                    Settings.DEFAULT_SCALE_VERTICAL);
            radius *= scaling;
            radius /= 100;
            knownIconCornerRadius = radius;
        }

        bg.setCornerRadius(knownIconCornerRadius * context.getResources().getDisplayMetrics().density);
        return bg;
    }

    private static int knownIconCornerRadius = -1;
    public static void invalidateIconCornerRadius(LauncherActivity a) {
        knownIconCornerRadius = -1;
        a.post(() -> {
            if (a.getAppAdapter() != null)
                a.getAppAdapter().setImageViewBackgrounds(() -> getAppBackgroundDrawable(a));
        });
    }
}
