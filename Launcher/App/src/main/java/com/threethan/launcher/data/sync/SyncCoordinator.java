package com.threethan.launcher.data.sync;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.datastore.DataStoreFile;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.util.LcDialog;

import java.io.File;

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

    /** Placeholder for future use if needed */
    private static String getSyncSuffix() {
        return "";
    }

    public static DataStoreEditor getDefaultDataStoreEditor(Context context) {
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

    public static void transferSettingsTo(Context context, String destinationPackage) {
        context = context.getApplicationContext();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo targetInfo = pm.getPackageInfo(destinationPackage, 0);
            PackageInfo myInfo = pm.getPackageInfo(context.getPackageName(), 0);

            long targetVersion = (android.os.Build.VERSION.SDK_INT >= 28)
                    ? targetInfo.getLongVersionCode()
                    : targetInfo.versionCode;
            long myVersion = (android.os.Build.VERSION.SDK_INT >= 28)
                    ? myInfo.getLongVersionCode()
                    : myInfo.versionCode;

            if (targetVersion < myVersion) {
                LcDialog.toast(context.getString(R.string.transfer_issue_other_older));
                return;
            } else if (targetVersion > myVersion) {
                LcDialog.toast(context.getString(R.string.transfer_issue_other_newer));
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            LcDialog.toast("Target app not installed?");
            return;
        }

        try {
            SyncFileProvider.grantAccessToTarget(context, destinationPackage);
            Intent syncIntent = new Intent("com.threethan.launcher.action.SYNC_SETTINGS");
            syncIntent.setPackage(destinationPackage);
            syncIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            syncIntent.putExtra(VariantHelper.SOURCE_EXTRA, context.getPackageName());
            context.startActivity(syncIntent);
        } catch (Exception e) {
            LcDialog.toast(context.getString(R.string.transfer_issue_generic) );
            Log.e("SyncCoordinator", "Error transferring settings to "+destinationPackage, e);
        }
    }
    public static void syncFromPackage(Context context, String fromPackage) {
        SyncFileProvider.copyDataFromOtherApp(context, fromPackage);
        DelayLib.delayed(() -> {
            if (context instanceof Activity activity) activity.finishAffinity();
            Compat.restartFully();
        }, 5000);
    }

    public static File getAbsoluteFileFor(Context context, String storeName) {
        if (context instanceof Activity) context = context.getApplicationContext();
        return DataStoreFile.dataStoreFile(context, storeName + ".preferences_pb");
    }
    public static File getRelativeFileFor(String storeName) {
        return new File("datastore/" + storeName + ".preferences_pb");
    }
}
