package com.threethan.launcher.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;

import com.threethan.launcher.activity.dialog.SyncReceiverDialog;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launchercore.util.CustomDialog;

public class SyncReceiveActivity extends LauncherActivity {
    private AlertDialog blockerDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras == null || !extras.containsKey(VariantHelper.SOURCE_EXTRA)) {
            Log.e("SyncReceiveActivity", "No extras or missing source extra");
            finishAffinity();
            return;
        }
        String source = extras.getString(VariantHelper.SOURCE_EXTRA);
        if (source == null || !VariantHelper.isKnownVariant(source)) {
            Log.e("SyncReceiveActivity", "Invalid or unknown source: " + source);
            finishAffinity();
            return;
        }
        blockerDialog = new CustomDialog.Builder(this)
                .setTitle("...")
                .setCancelable(false)
                .setOnDismissListener(v -> finishAffinity())
                .show();

        AlertDialog mainDialog = new SyncReceiverDialog(this, source).show();
        if (mainDialog != null) {
            mainDialog.setOnDismissListener(v -> {
                blockerDialog.dismiss();
                finishAffinity();
            });
        } else {
            blockerDialog.dismiss();
            finishAffinity();
        }
    }

    @Override
    protected void onDestroy() {
        blockerDialog.dismiss();
        super.onDestroy();
    }
}
