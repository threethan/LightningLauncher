package com.threethan.launcher.helper;

import android.app.Activity;

import androidx.datastore.DataStoreFile;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launchercore.lib.FileLib;

import java.io.File;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Backs up and loads settings between the internal {@link androidx.datastore.core.DataStore}
 * and an arbitrary user-accessible file in android/data/
 */
public abstract class SettingsSaver {
    public static final String EXPORT_FILE_NAME = "ExportedConfiguration.preferences_pb";
    public static final String EXPORT_FILE_NAME_SORT = "ExportedSort.preferences_pb";

    /**
     * Saves the contents of the DataStore to a file
     * @param activity used for getting package name and data store paths
     */
    public static void save(Activity activity) {
        if (!BasicDialog.validateVariantWithNotify()) return;

        File prefs1 = DataStoreFile.dataStoreFile(activity, SyncCoordinator.DATA_STORE_DEFAULT + ".preferences_pb");
        File prefs2 = DataStoreFile.dataStoreFile(activity, SyncCoordinator.DATA_STORE_PER_APP + ".preferences_pb");
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME);
        assert exportPath != null;

        boolean ignored1 = Objects.requireNonNull(export.getParentFile()).mkdirs();
        FileLib.delete(export);

        if (FileLib.copy(prefs1, export) && FileLib.copy(prefs1, export)
         && FileLib.copy(prefs2, export) && FileLib.copy(prefs2, export))
            BasicDialog.toast(activity.getText(R.string.saved_settings),
                "Android/Data/"+activity.getPackageName()+"/"+EXPORT_FILE_NAME,
                false);
        else BasicDialog.toast(activity.getString(R.string.saved_settings_error));
    }
    public static void saveSort(Activity activity) {
        if (!BasicDialog.validateVariantWithNotify()) return;

        File prefs = DataStoreFile.dataStoreFile(activity, SyncCoordinator.DATA_STORE_SORT + ".preferences_pb");
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
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME);

        BasicDialog.toast(activity.getString(R.string.settings_load));

        SyncCoordinator.getDefaultDataStore(activity).copyFrom(export);

        BasicDialog.toast(activity.getString(R.string.saved_settings_loading));
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                int pid = android.os.Process.myPid();
                android.os.Process.killProcess(pid);
            }
        }, 1500);    }
    public synchronized static void loadSort(Activity activity) {
        File exportPath = activity.getExternalFilesDir("");
        File export = new File(exportPath, EXPORT_FILE_NAME_SORT);

        BasicDialog.toast(activity.getString(R.string.settings_load));

        SyncCoordinator.getSortDataStore(activity).copyFrom(export);

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
