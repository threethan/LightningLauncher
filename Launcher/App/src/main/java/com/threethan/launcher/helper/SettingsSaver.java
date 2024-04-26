package com.threethan.launcher.helper;

import android.app.Activity;
import android.os.Build;

import androidx.datastore.DataStoreFile;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.lib.FileLib;

import java.io.File;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Backs up and loads settings between the internal {@link androidx.datastore.core.DataStore}
 * and an arbitrary user-accessible file in android/data/
 */
public abstract class SettingsSaver {
    private static final String DATA_STORE_NAME = "default";
    private static final String DATA_STORE_NAME_SORT = "sort";
    public static final String EXPORT_FILE_NAME = "ExportedConfiguration.preferences_pb";
    public static final String EXPORT_FILE_NAME_SORT = "ExportedSort.preferences_pb";

    /**
     * Saves the contents of the DataStore to a file
     * @param activity used for getting package name and data store paths
     */
    public static void save(Activity activity) {

        File prefs = DataStoreFile.dataStoreFile(activity, DATA_STORE_NAME+".preferences_pb");
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME);
        assert exportPath != null;

        boolean ignored1 = Objects.requireNonNull(export.getParentFile()).mkdirs();
        FileLib.delete(export);

        if (FileLib.copy(prefs, export) && FileLib.copy(prefs, export))
            BasicDialog.toast(activity.getString(R.string.saved_settings),
                "Android/Data/"+activity.getPackageName()+"/"+EXPORT_FILE_NAME,
                false);
        else BasicDialog.toast(activity.getString(R.string.saved_settings_error));
    }
    public static void saveSort(Activity activity) {

        File prefs = DataStoreFile.dataStoreFile(activity, DATA_STORE_NAME_SORT+".preferences_pb");
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME_SORT);
        assert exportPath != null;

        boolean ignored1 = Objects.requireNonNull(export.getParentFile()).mkdirs();
        FileLib.delete(export);

        if (FileLib.copy(prefs, export) && FileLib.copy(prefs, export))
            BasicDialog.toast(activity.getString(R.string.saved_settings),
                    "Android/Data/"+activity.getPackageName()+"/"+EXPORT_FILE_NAME_SORT,
                    false);
        else BasicDialog.toast(activity.getString(R.string.saved_settings_error));
    }
    /**
     * Loads the contents of the DataStore from a file.
     * <p>
     * Warning: No error checking is done!
     * @param activity used for getting package name and data store paths
     */
    public synchronized static void load(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            BasicDialog.toast("Android API too old!");
            return;
        }
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME);

        BasicDialog.toast(activity.getString(R.string.settings_load));

        new DataStoreEditor(activity, DATA_STORE_NAME).copyFrom(export);

        BasicDialog.toast(activity.getString(R.string.saved_settings_loading));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                int pid = android.os.Process.myPid();
                android.os.Process.killProcess(pid);
            }
        }, 1500);    }
    public synchronized static void loadSort(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            BasicDialog.toast("Android API too old!");
            return;
        }
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME_SORT);

        BasicDialog.toast(activity.getString(R.string.settings_load));

        new DataStoreEditor(activity, DATA_STORE_NAME_SORT).copyFrom(export);

        BasicDialog.toast(activity.getString(R.string.saved_settings_loading));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                int pid = android.os.Process.myPid();
                android.os.Process.killProcess(pid);
            }
        }, 1500);
    }

    /**
     * Check if there is a valid file from which we can load a backup
     * @param activity used for getting package name and data store paths
     * @return If the file exists in the correct location
     */
    public static boolean canLoad(LauncherActivity activity) {
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME);
        return export.exists();
    }
    public static boolean canLoadSort(LauncherActivity activity) {
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME_SORT);
        return export.exists();
    }
}
