package com.threethan.launcher.data;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import com.threethan.launcher.BuildConfig;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.chainload.ChainLoadActivity;
import com.threethan.launcher.activity.chainload.ChainLoadActivityHuge;
import com.threethan.launcher.activity.chainload.ChainLoadActivityLarge;
import com.threethan.launcher.activity.chainload.ChainLoadActivityPhone;
import com.threethan.launcher.activity.chainload.ChainLoadActivitySmall;
import com.threethan.launcher.activity.chainload.ChainLoadActivityWide;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.Platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This abstract class just stores a number of default settings used by other classes;
 * it's a glorified text file.
 * <p>
 * Anything that's prefixed with "KEY_" is a string which is used to save/load a given setting
 * to/from sharedPreferences
 * <p>
 * It does not contain any code. Actual access of settings occurs in various other classes,
 * primarily SettingsManager
 */
public abstract class Settings {
    // Backgrounds
    public static final int[] BACKGROUND_DRAWABLES = {
            R.drawable.bg_px_blue,
            R.drawable.bg_px_grey,
            R.drawable.bg_px_red,
            R.drawable.bg_px_white,
            R.drawable.bg_px_orange,
            R.drawable.bg_px_green,
            R.drawable.bg_px_purple,
            R.drawable.bg_meta_dark,
            R.drawable.bg_meta_light,
            PlatformExt.supportsTransparentBackgroundOpt()
                ? R.drawable.bg_trans : R.drawable.bg_warm_dark,
    };
    public static final int[] BACKGROUND_COLORS = {
            0xFF25374f,
            0xFFeaebea,
            0xFFf89b94,
            0xFFd9d4da,
            0xFFf9ce9b,
            0xFFe4eac8,
            0xFF74575c,
            0xFF323232,
            0xFFc6d1df,
            PlatformExt.supportsTransparentBackgroundOpt()
                    ? Color.TRANSPARENT :
                    0xFF140123,
    };
    public static final boolean[] BACKGROUND_DARK = {
            true,
            false,
            false,
            false,
            false,
            false,
            true,
            true,
            false,
            true,
    };
    // Theme/background
    public static final String KEY_FORCE_UNTRANSLATED = "KEY_FORCE_UNTRANSLATED";
    public static final String UNTRANSLATED_LANGUAGE = "en";
    public static final String KEY_BACKGROUND = "KEY_CUSTOM_THEME";
    public static final String KEY_BACKGROUND_ALPHA = "KEY_CUSTOM_ALPHA";
    public static final String KEY_BACKGROUND_ALPHA_PRESERVE = "KEY_BACKGROUND_ALPHA_PRESERVE";
    public static final String KEY_BACKGROUND_BLUR = "KEY_BACKGROUND_BLUR_CLAMP";
    public static final String KEY_DARK_MODE = "KEY_DARK_MODE";
    public static final String KEY_GROUPS_ENABLED = "KEY_GROUPS_ENABLED";
    public static final String KEY_GROUPS_WIDE = "KEY_GROUPS_WIDE";
    public static final String KEY_DETAILS_LONG_PRESS = "KEY_DETAILS_LONG_PRESS";
    public static final String KEY_SEARCH_WEB = "KEY_SEARCH_WEB";
    public static final String KEY_SEARCH_HIDDEN = "KEY_SEARCH_HIDDEN";
    /** @noinspection ConstantValue*/
    public static final int DEFAULT_BACKGROUND_VR = BuildConfig.FLAVOR.equals("sideload") ? 9 : 0;
    public static final int DEFAULT_BACKGROUND_TV = 9;
    public static final int DEFAULT_ALPHA = Platform.isQuestGen3() ? 0 : (Platform.isQuest() ? 128 : 255);
    public static final boolean DEFAULT_BACKGROUND_ALPHA_PRESERVE
            = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Platform.isQuest();
    public static final boolean DEFAULT_BACKGROUND_BLUR = true;
    public static final boolean DEFAULT_DARK_MODE = true;
    public static final boolean DEFAULT_GROUPS_ENABLED = !Platform.isPhone();
    public static final boolean DEFAULT_GROUPS_WIDE = false;
    public static final String KEY_LIST_HORIZONTAL = "KEY_LIST_HORIZONTAL";
    public static final boolean DEFAULT_LIST_HORIZONTAL = false;
    public static boolean DEFAULT_DETAILS_LONG_PRESS = false;
    public static final boolean DEFAULT_SEARCH_WEB = true;
    public static final boolean DEFAULT_SEARCH_HIDDEN = true;
    public static final String CUSTOM_BACKGROUND_PATH = "background.png";

    // Basic UI keys
    public static final String KEY_SCALE_VERTICAL = "KEY_CUSTOM_SCALE";
    public static final String KEY_SCALE_HORIZONTAL = "KEY_CUSTOM_SCALE_HORIZONTAL";
    public static final String KEY_MARGIN_VERTICAL = "KEY_CUSTOM_MARGIN";
    public static final String KEY_MARGIN_HORIZONTAL = "KEY_CUSTOM_MARGIN_HORIZONTAL";
    public static final String KEY_ICON_CORNER_RADIUS_VERTICAL = "KEY_ICON_CORNER_RADIUS";
    public static final String KEY_ICON_CORNER_RADIUS_HORIZONTAL = "KEY_ICON_CORNER_RADIUS_HORIZONTAL";
    public static final int DEFAULT_SCALE_VERTICAL = 110;
    public static final int DEFAULT_SCALE_HORIZONTAL = 110;
    public static final int MAX_SCALE = 160;
    public static final int MIN_SCALE = 60;
    public static final int DEFAULT_MARGIN_HORIZONTAL = 0;
    public static final int DEFAULT_MARGIN_VERTICAL = 10;
    public static final int DEFAULT_ICON_CORNER_RADIUS_VERTICAL = 16;
    public static final int DEFAULT_ICON_CORNER_RADIUS_HORIZONTAL = 999;
    public static final String KEY_NEW_MULTITASK = "KEY_NEWER_MULTITASK";
    public static final boolean DEFAULT_NEW_MULTITASK = true;
    public static final String KEY_ALLOW_CHAIN_LAUNCH = "KEY_ALLOW_CHAIN_LAUNCH";
    public static final boolean DEFAULT_ALLOW_CHAIN_LAUNCH = true;
    public static final String KEY_EDIT_MODE = "KEY_EDIT_MODE";
    public static final String KEY_SEEN_ADDONS = "KEY_SEEN_ADDONS";
    public static final String KEY_SEEN_DMQS = "KEY_SEEN_DMQS";
    public static final String KEY_ADDONS_VR_TYPE_KNOWN = "KEY_ADDONS_VR_TYPE_KNOWN";
    public static final String KEY_ADDONS_VR_HAS_NAVIGATOR = "KEY_ADDONS_VR_HAS_NAVIGATOR";

    // banner-style display by app type
    public static final String KEY_BANNER = "prefTypeIsWide";
    public static final Map<App.Type, Boolean> FALLBACK_BANNER = new HashMap<>();
    static {
        FALLBACK_BANNER.put(App.Type.PHONE, false);
        FALLBACK_BANNER.put(App.Type.WEB, false);
        FALLBACK_BANNER.put(App.Type.VR, true);
        FALLBACK_BANNER.put(App.Type.TV, true);
        FALLBACK_BANNER.put(App.Type.PANEL, false);
    }
    public static final String KEY_FORCED_BANNER = "KEY_FORCED_BANNER";
    public static final String KEY_FORCED_SQUARE = "KEY_FORCED_SQUARE";


    // show names by display type
    public static final String KEY_SHOW_NAMES_SQUARE = "KEY_CUSTOM_NAMES";
    public static final String KEY_SHOW_NAMES_BANNER = "KEY_CUSTOM_NAMES_WIDE";
    public static final String KEY_SHOW_TIMES_BANNER = "KEY_SHOW_PLAYTIMES_WIDE";
    public static final boolean DEFAULT_SHOW_NAMES_SQUARE = true;
    public static final boolean DEFAULT_SHOW_NAMES_BANNER = false;
    public static final boolean DEFAULT_SHOW_TIMES_BANNER = true;

    public static final String KEY_GROUPS = "prefAppGroups";
    public static final String KEY_GROUP_APP_LIST = "prefAppList";
    public static final String KEY_SELECTED_GROUPS = "prefSelectedGroups";
    public static final String KEY_WEBSITE_LIST = "prefWebAppNames";
    public static final String KEY_LAUNCH_SIZE = "prefLaunchSize";
    public static final String KEY_LAUNCH_BROWSER = "prefLaunchBrowser";
    public static final String KEY_DEFAULT_BROWSER = "KEY_DEFAULT_BROWSER";

    /** @noinspection rawtypes */
    public static final Class[] launchSizeClasses = {
            null,
            ChainLoadActivityPhone.class,
            ChainLoadActivitySmall.class,
            ChainLoadActivity.class,
            ChainLoadActivityLarge.class,
            ChainLoadActivityHuge.class,
            ChainLoadActivityWide.class,
    };
    // group
    public static final String KEY_DEFAULT_GROUP = "prefDefaultGroupForType";
    public static String getDefaultGroupForType(Context context, App.Type type) {
        return switch (type) {
            case VR ->
                    StringLib.setPreChar(context.getString(R.string.default_group_vr), StringLib.STAR, true);
            case TV ->
                    StringLib.setPreChar(context.getString(R.string.default_group_media), StringLib.STAR, true);
            default -> context.getString(R.string.default_group_2d);
        };
    }

    public static final int MAX_GROUPS = 20;
    public static final int GROUP_WIDTH_DP = 150;
    public static final int GROUP_WIDTH_DP_WIDE = 300;
    static {
        //noinspection ConstantValue
        assert GROUP_WIDTH_DP > 0;
        //noinspection ConstantValue
        assert GROUP_WIDTH_DP_WIDE > 0;
    }

    public static final String HIDDEN_GROUP = "HIDDEN!";
    public static final String UNSUPPORTED_GROUP = "UNSUPPORTED!";

    public static final String KEY_NEWLY_ADDED_BASELINE = "KEY_NEWLY_ADDED_BASELINE";
    public static final String KEY_NEWLY_ADDED = "KEY_NEWLY_ADDED";
    public static final String PREF_NEWLY_ADDED_TIME = "prefNewlyAddedTime";
    public static final String PREF_LAST_LAUNCHED_TIME = "prefRecentlyLaunchedTime";
    public static final String KEY_TAG_MAX_DURATION = "KEY_TAG_MAX_DURATION";
    public static final int DEFAULT_KEY_TAG_MAX_DURATION = 15; // minutes
    public static final List<Integer> NEWLY_ADDED_DURATION_OPTIONS = List.of(0, 15, 30, 90);

    // Variants
    public static final String KEY_ALLOW_SHORTCUTS = "KEY_ALLOW_SHORTCUTS";
    public static final boolean DEFAULT_ALLOW_SHORTCUTS = true;

    // Top bar
    public static final String KEY_SHOW_STATUS = "KEY_SHOW_STATUS";
    public static final boolean DEFAULT_SHOW_STATUS = !Platform.isPhone();
    public static final String KEY_SHOW_SEARCH = "KEY_SHOW_SEARCH";
    public static final boolean DEFAULT_SHOW_SEARCH = true;
    public static final String KEY_SHOW_SORT = "KEY_SHOW_SORT";
    public static final boolean DEFAULT_SHOW_SORT = true;
    public static final String KEY_SHOW_SETTINGS = "KEY_SHOW_SETTINGS";
    public static final boolean DEFAULT_SHOW_SETTINGS = true;
    public static final String KEY_SORT = "KEY_SORT_MODE";
    public static final int DEFAULT_SORT = 0; // Standard

    public static final String KEY_REDUCE_MOTION = "KEY_REDUCE_MOTION";
    public static final boolean DEFAULT_REDUCE_MOTION = true; //TODO: False
}
