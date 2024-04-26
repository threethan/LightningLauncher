package com.threethan.launcher.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** noinspection CommentedOutCode
 * Stores a list of panel apps, invalid apps, and label overrides.
 * It's in it's own class since it doesn't really fit anywhere else.
 * <p>
 * Also has the package name of the explore package.
 */
public abstract class AppData {
    protected static final List<PanelApplicationInfo> panelAppList;
    public static final String EXPLORE_PACKAGE = "com.oculus.explore";

    static {
        panelAppList = new ArrayList<>();

        panelAppList.add(new PanelApplicationInfo("Meta Quest TV", "com.oculus.tv"));

        // Autodetected
//        panelAppList.add(new PanelApp("Meta Quest Scoreboards", "com.oculus.gamingactivity"));
//        panelAppList.add(new PanelApp("Meta Quest Browser", "com.oculus.browser"));
//        panelAppList.add(new PanelApp("Meta Quest Move", "com.oculus.fitnesstracker"));
//        panelAppList.add(new PanelApp("Meta Quest Store", "com.oculus.store"));
//        panelAppList.add(new PanelApp("Account Center", "com.oculus.accountscenter"));
//        panelAppList.add(new PanelApp("Meta Quest Guide", "com.oculus.helpcenter"));

//        panelAppList.add(new PanelApp("Meta Quest Browser (Intent)", "systemux://browser"));
//        panelAppList.add(new PanelApp("Meta Quest Store (Intent)", "systemux://store"));
        panelAppList.add(new PanelApplicationInfo("Camera", "systemux://sharing"));
        panelAppList.add(new PanelApplicationInfo("People", "systemux://aui-social-v2"));
//        panelAppList.add(new PanelApp("Quick Settings", "systemux://quick_settings"));
        panelAppList.add(new PanelApplicationInfo("Quest Settings", "systemux://settings"));
//        panelAppList.add(new PanelApp("Notifications", "systemux://notifications"));
//        panelAppList.add(new PanelApp("Profile", "systemux://aui-profile"));
//        panelAppList.add(new PanelApp("Explore", "systemux://explore"));// Can just use the explore app
        panelAppList.add(new PanelApplicationInfo("Events", "systemux://events"));// Part of Explore
        panelAppList.add(new PanelApplicationInfo("File Manager", "systemux://file-manager"));

        panelAppList.add(new PanelApplicationInfo("Remote Display", "com.oculus.remotedesktop"));
    }

    public static List<PanelApplicationInfo> getFullPanelAppList() {
        return panelAppList;
    }

    // Whether we should try to detect additional panel apps that are not explicitly set
    public static boolean AUTO_DETECT_PANEL_APPS = true;

    public static final Map<String, String> labelOverrides = new HashMap<>();
    static {
        labelOverrides.put("com.oculus.gamingactivity", "Meta Quest Scoreboards");
        labelOverrides.put("com.oculus.helpcenter", "Meta Quest Guide");
        labelOverrides.put("com.android.settings", "Android Settings");
    }
    public static final List<String> invalidAppsList = new ArrayList<>();
    static {
        invalidAppsList.add("com.oculus.systemutilities");
        invalidAppsList.add("com.oculus.systemresource"); // Jank AF
        invalidAppsList.add("com.oculus.socialplatform");
        invalidAppsList.add("com.oculus.guidebook");
        invalidAppsList.add("com.oculus.firsttimenux");
        invalidAppsList.add("com.oculus.systemux");
        invalidAppsList.add("com.oculus.accountscenter"); // Accessible through settings
        invalidAppsList.add("com.oculus.avatareditor"); // Accessible through profile
    }

}
