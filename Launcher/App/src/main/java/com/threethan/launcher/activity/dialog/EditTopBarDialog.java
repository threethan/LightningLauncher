package com.threethan.launcher.activity.dialog;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;

/**
 * Dialog for editing the top bar settings in the launcher.
 */
public class EditTopBarDialog extends BasicDialog<LauncherActivity> {
    public EditTopBarDialog(LauncherActivity activity) {
        super(activity, R.layout.dialog_edit_top_bar);
    }

    /** Shows and initializes the dialog.
     * @return The AlertDialog instance.
     */
    @Nullable
    @Override
    public AlertDialog show() {
        AlertDialog d = super.show();
        if (d == null) return null;

        d.findViewById(R.id.dismissButton).setOnClickListener(v -> d.dismiss());

        bindToggle(d.findViewById(R.id.tbeStatus), Settings.KEY_SHOW_STATUS, Settings.DEFAULT_SHOW_STATUS);

        bindToggle(d.findViewById(R.id.tbeGroups), Settings.KEY_GROUPS_ENABLED, Settings.DEFAULT_GROUPS_ENABLED, true);

        bindToggle(d.findViewById(R.id.tbeSort), Settings.KEY_SHOW_SORT, Settings.DEFAULT_SHOW_SORT);
        bindToggle(d.findViewById(R.id.tbeSearch), Settings.KEY_SHOW_SEARCH, Settings.DEFAULT_SHOW_SEARCH);
        bindToggle(d.findViewById(R.id.tbeSettings), Settings.KEY_SHOW_SETTINGS, Settings.DEFAULT_SHOW_SETTINGS);

        return d;
    }

    private void bindToggle(ViewGroup sectionView, String SettingKey, boolean defaultValue) {
        bindToggle(sectionView, SettingKey, defaultValue, false);
    }
    private void bindToggle(ViewGroup sectionView, String SettingKey, boolean defaultValue, boolean refreshGroups) {
        Switch toggle = sectionView.findViewById(R.id.toggle);
        if (toggle.getParent() instanceof View parentView) {
            parentView.setOnClickListener(v -> toggle.performClick());
        }
        toggle.setChecked(a.dataStoreEditor.getBoolean(SettingKey, defaultValue));
        toggle.setOnCheckedChangeListener((b, checked) -> {
            a.dataStoreEditor.putBoolean(SettingKey, checked);
            a.launcherService.forEachActivity(a -> a.postDelayed(refreshGroups ? () -> {
                a.setEditMode(true);
                a.setEditMode(false);
            } : () -> {
                a.updateToolBars();
                a.postDelayed(() -> {
                    a.updateToolBars();
                    a.post(() -> a.updateGridLayouts(true)); // Needed to update top-padding on list
                }, 500);
            }, 100));
        });
    }
}
