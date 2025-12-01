package com.threethan.launcher.activity.dialog;

import android.app.AlertDialog;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;
import com.threethan.launchercore.util.LcDialog;

public class MQSDialog extends LcDialog<LauncherActivity> {
    private final LauncherActivity activity;
    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     *
     * @param activity  Context to show the dialog
     */
    public MQSDialog(LauncherActivity activity) {
        super(activity, R.layout.dialog_mqs);
        this.activity = activity;
    }

    @Nullable
    @Override
    public AlertDialog show() {
        AlertDialog dialog = super.show();
        if (dialog == null) return null;
        Button dismissButton = dialog.findViewById(R.id.dismiss);
        dismissButton.setEnabled(false);
        dismissButton.postDelayed(() -> dismissButton.setEnabled(true), 3000);
        dismissButton.setOnClickListener(v -> {
            dialog.dismiss();
            activity.getDataStoreEditor().putBoolean(Settings.KEY_SEEN_DMQS, true);
        });
        return dialog;
    }
}
