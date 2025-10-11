package com.threethan.launcher.data.sync;

import android.app.Activity;
import android.content.Context;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.DataStoreEditor;

import kotlin.NotImplementedError;

public abstract class SyncCoordinator {

    public static final String DATA_STORE_DEFAULT = "default";
    public static final String DATA_STORE_SORT = "sort";
    public static final String DATA_STORE_PER_APP = "perApp";

    private static String getDefaultDataStoreName() {
        return "default" + getSyncSuffix();
    }

    private static String getSortDataStoreName() {
        return "sort" + getSyncSuffix();
    }

    /** Call to perform sync when the app is started */
    public static void onStart(Activity activity) {
//        SyncFileProvider.copyDataFromOtherApp(activity);
    }

    private static String getSyncSuffix() {
        return "";
    }

    /** Call to propagate changed settings to other apps */
    public static void onStop(Activity activity) {
//        SyncFileProvider.copyDataToOtherApps(activity);
    }

    public static DataStoreEditor getDefaultDataStore(Context context) {
        context = context.getApplicationContext();
        return new DataStoreEditor(context, getDefaultDataStoreName());
    }

    public static DataStoreEditor getSortDataStore(Context context) {
        context = context.getApplicationContext();
        return new DataStoreEditor(context, getSortDataStoreName());
    }

    public static DataStoreEditor getPerAppDataStore(Context context) {
        context = context.getApplicationContext();
        return new DataStoreEditor(context, "perApp");
    }

    public static void transferSettingsTo(LauncherActivity a, String packageName, boolean b) {
        //TODO!!!!!
        throw new NotImplementedError();
    }
}
