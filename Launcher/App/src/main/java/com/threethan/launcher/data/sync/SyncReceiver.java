package com.threethan.launcher.data.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.helper.Compat;

public class SyncReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SyncReceiver", "Received intent to sync: " + intent);

        if (intent.hasExtra("grant") && intent.getBooleanExtra("grant", true))
            SyncFileProvider.grantAccessToOtherApp(context);

        DataStoreEditor.clearInstances();
        Compat.restartFully();
    }
}
