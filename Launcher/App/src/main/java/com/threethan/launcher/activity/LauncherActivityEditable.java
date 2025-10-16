package com.threethan.launcher.activity;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.adapter.LauncherAppsAdapter;
import com.threethan.launcher.activity.adapter.GroupsAdapter;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.activity.view.EditTextWatched;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.LcDialog;
import com.threethan.launchercore.util.Platform;
import com.threethan.launchercore.view.LcBlurCanvas;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
    The class handles the additional interface elements and properties related to edit mode.
    This includes the bottom bar & dialog for adding websites, but not the dialogs to edit an
    individual app or group.
 */

public class LauncherActivityEditable extends LauncherActivity {
    @Nullable
    protected Boolean editMode = null;
    private boolean isCopying = false;

    /**
     * Used to track changes in a hashset and track changes made to selected apps
     */
    private class ConnectedHashSet extends HashSet<String> {
        @Override
        public boolean add(String s) {
            Objects.requireNonNull(getAppAdapter()).notifySelectionChange(s);
            return super.add(s);
        }
        @Override
        public boolean remove(@Nullable Object o) {
            Objects.requireNonNull(getAppAdapter()).notifySelectionChange((String) o);
            return super.remove(o);
        }

        @Override
        public void clear() {
            for (String s : this) Objects.requireNonNull(getAppAdapter()).notifySelectionChange(s);
            super.clear();
        }
    }
    public final HashSet<String> currentSelectedApps = new ConnectedHashSet();
    // Startup
    @Override
    protected void init() {
        super.init();
        View addWebsiteButton = rootView.findViewById(R.id.addWebsite);
        addWebsiteButton.setOnClickListener(view -> addWebsite());
        View stopEditingButton = rootView.findViewById(R.id.stopEditing);
        stopEditingButton.setOnClickListener(view -> setEditMode(false));
    }

    @SuppressLint("UseCompatTextViewDrawableApis")
    @Override
    public void refreshInterface() {
        dataStoreEditor = SyncCoordinator.getDefaultDataStore(this);

        if (editMode == null) setEditMode(dataStoreEditor.getBoolean(Settings.KEY_EDIT_MODE, false));

        super.refreshInterface();

        final View editFooter = rootView.findViewById(R.id.editFooter);
        if (editMode) {
            // Edit bar theming and actions
            editFooter.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));

            final TextView selectionHintText = rootView.findViewById(R.id.selectionHintText);
            final ImageView uninstallButton = rootView.findViewById(R.id.uninstallBulk);
            final Spinner actionSpinner = rootView.findViewById(R.id.editActionSpinner);
            actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    isCopying = i == 1;
                    updateSelectionHint();
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {}
            });

            ((TextView) rootView.findViewById(R.id.stopEditing))
                    .setCompoundDrawableTintList(ColorStateList.valueOf(darkMode ? Color.WHITE : Color.BLACK));

            selectionHintText.setOnClickListener((view) -> {
                if (currentSelectedApps.isEmpty()) {
                    final LauncherAppsAdapter adapter = getAppAdapter();
                    if (adapter != null)
                        for (int i=0; i<adapter.getItemCount(); i++)
                            currentSelectedApps.add(adapter.getItem(i).packageName);
                    selectionHintText.setText(R.string.selection_hint_all);
                } else {
                    currentSelectedApps.clear();
                    selectionHintText.setText(R.string.selection_hint_cleared);
                }
                selectionHintText.postDelayed(this::updateSelectionHint, 2000);
                rootView.findViewById(R.id.uninstallBulk).setVisibility(View.GONE);
            });
            selectionHintText.setOnClickListener((view) -> {
                if (currentSelectedApps.isEmpty()) {
                    final LauncherAppsAdapter adapter = getAppAdapter();
                    if (adapter != null)
                        for (int i=0; i<adapter.getItemCount(); i++)
                            currentSelectedApps.add(adapter.getItem(i).packageName);
                    selectionHintText.setText(R.string.selection_hint_all);
                } else {
                    currentSelectedApps.clear();
                    selectionHintText.setText(R.string.selection_hint_cleared);
                }
                selectionHintText.postDelayed(this::updateSelectionHint, 2000);
                rootView.findViewById(R.id.uninstallBulk).setVisibility(View.GONE);
            });
            uninstallButton.setOnClickListener(view -> {
                int delay = 0;
                for (String currentSelectedApp : currentSelectedApps) {
                    mainView.postDelayed(() -> AppExt.uninstall(currentSelectedApp), delay);
                    if (!App.isWebsite(currentSelectedApp)) delay += 1000;
                }
            });
        }
        if (editFooter.getVisibility() == View.GONE && editMode) {
            editFooter.setTranslationY(100f);
            editFooter.setVisibility(View.VISIBLE);
        }
        ObjectAnimator aF = ObjectAnimator.ofFloat(editFooter, "TranslationY", editMode ?0f:100f);
        aF.addUpdateListener(va -> editFooter.invalidate());
        aF.setDuration(200);
        aF.start();
        if (!editMode) editFooter.postDelayed(() -> {
            if (!editMode) editFooter.setVisibility(View.GONE);
        }, 200);

        if (!editMode) {
            currentSelectedApps.clear();
            updateSelectionHint();
        }
    }

    @Override
    public void clickGroup(int position, View source) {
        if (Boolean.FALSE.equals(editMode)) {
            super.clickGroup(position, source);
            return;
        }

        lastSelectedGroup = position;
        final List<String> groupsSorted = settingsManager.getAppGroupsSorted(false);

        // If the new group button was selected, create and select a new group
        if (position >= groupsSorted.size()) {
            final String ignored = settingsManager.addGroup();
            super.clickGroup(position-1, source); //Auto-move selection and select new group
            refreshInterface();
            postDelayed(() -> clickGroup(position-1, source), 500); //Auto-move selection
            return;
        }
        final String group = groupsSorted.get(position);

        // Move apps if any are selected
        if (!currentSelectedApps.isEmpty()) {
            GroupsAdapter groupsAdapter = getGroupAdapter();
            if (groupsAdapter != null)
                for (String app : currentSelectedApps) {
                    if (isCopying) groupsAdapter.copyAppToGroup(app, group);
                    else groupsAdapter.moveAppToGroup(app, group);
                }

            TextView selectionHintText = rootView.findViewById(R.id.selectionHintText);
            selectionHintText.setText( currentSelectedApps.size()==1 ?
                    getString(isCopying ? R.string.selection_copied_single :R.string.selection_moved_single, group) :
                    getString(isCopying ? R.string.selection_copied_multiple :R.string.selection_moved_multiple, currentSelectedApps.size(), group)
            );
            rootView.findViewById(R.id.uninstallBulk).setVisibility(View.GONE);
            selectionHintText.postDelayed(this::updateSelectionHint, 2000);

            currentSelectedApps.clear();

            SettingsManager.writeGroupsAndSort();
            refreshInterface();
        } else super.clickGroup(position, source);
    }

    // Function overrides
    @Override
    public void setEditMode(boolean value) {
        editMode = value;
        if (!editMode) currentSelectedApps.clear();
        if (dataStoreEditor == null) return;

        View topGradient = rootView.findViewById(R.id.topGradient);
        topGradient.setVisibility(editMode || groupsEnabled ? View.VISIBLE : View.GONE);

        dataStoreEditor.putBoolean(Settings.KEY_EDIT_MODE, editMode);
        final View focused = getCurrentFocus();
        refreshInterface();
        if (focused != null) {
            focused.clearFocus();
            focused.post(focused::requestFocus);
        }
        LcBlurCanvas.useRenderRect = false;
        postDelayed(() -> LcBlurCanvas.useRenderRect = !editMode, 500);
        updateToolBars();
    }

    @Override
    public boolean selectApp(String app) {
        if (currentSelectedApps.contains(app)) {
            currentSelectedApps.remove(app);
            updateSelectionHint();
            return false;
        } else {
            currentSelectedApps.add(app);
            updateSelectionHint();
            return true;
        }
    }

    @Override
    protected void startWithExistingView() {
        super.startWithExistingView();
        // Load edit things if loading from an existing activity
        final View editFooter = rootView.findViewById(R.id.editFooter);
        if (editFooter.getVisibility() == View.VISIBLE)
            launcherService.forEachActivity(LauncherActivity::refreshInterface);
    }

    @Override
    public void refreshAppList() {
        super.refreshAppList();

        Set<String> webApps = dataStoreEditor.getStringSet(Settings.KEY_WEBSITE_LIST, new HashSet<>());
        Set<String> packages = getAllPackages();

        try {
            currentSelectedApps.removeIf(appPackage
                    -> !packages.contains(appPackage) && !webApps.contains(appPackage));
        } catch (ConcurrentModificationException ignored) {}
        updateSelectionHint();
    }

    @Override
    public boolean isSelected(String app) { return currentSelectedApps.contains(app); }
    @Override
    protected int getBottomBarHeight() { return Boolean.TRUE.equals(editMode) ? dp(60) : 0; }
    @Override
    public boolean isEditing() { return Boolean.TRUE.equals(editMode); }
    @Override
    public boolean canEdit() { return groupsEnabled; }

    // Utility functions
    void updateSelectionHint() {
        TextView selectionHintText = rootView.findViewById(R.id.selectionHintText);
        final View uninstallButton = rootView.findViewById(R.id.uninstallBulk);
        uninstallButton.setVisibility(currentSelectedApps.isEmpty()
                && PlatformExt.canUninstall() ? View.GONE : View.VISIBLE);

        final int size = currentSelectedApps.size();
        runOnUiThread(() -> {
            if (size == 0)      selectionHintText.setText(R.string.selection_hint_none);
            else if (size == 1) selectionHintText.setText(isCopying ? R.string.selection_hint_single_copy : R.string.selection_hint_single_move);
            else selectionHintText.setText(getString(isCopying ? R.string.selection_hint_multiple_copy : R.string.selection_hint_multiple_move, size));
        });
    }

    public void addWebsite() {
        
        AlertDialog dialog = new LcDialog<>(this, R.layout.dialog_add_website).show();

        // Set group to (one of) selected
        String group;
        final ArrayList<String> appGroupsSorted = settingsManager.getAppGroupsSorted(true);
        if (!appGroupsSorted.isEmpty()) group = appGroupsSorted.get(0);
        else group = SettingsManager.getDefaultGroupFor(App.Type.PHONE);

        if (dialog == null) return;

        dialog.findViewById(R.id.cancel).setOnClickListener(view -> dialog.cancel());
        ((Button) dialog.findViewById(R.id.install)).setText(getString(R.string.add_website_group, group));
        EditTextWatched urlEdit = dialog.findViewById(R.id.appUrl);
        urlEdit.post(urlEdit::requestFocus);

        TextView badUrl  = dialog.findViewById(R.id.badUrl);
        urlEdit.setOnEdited(url -> {
            if (StringLib.isInvalidUrl(url) && url.contains(".")) url = "https://" + url;
            badUrl .setVisibility(StringLib.isInvalidUrl(url)
                    ? View.VISIBLE : View.GONE);
        });

        dialog.findViewById(R.id.install).setOnClickListener(view -> {
            String url  = urlEdit.getText().toString().toLowerCase();
            if (StringLib.isInvalidUrl(url)) url = "https://" + url;
            if (StringLib.isInvalidUrl(url)) return;
            PlatformExt.addWebsite(dataStoreEditor, url);
            if (!url.contains("://")) url = "https://" + url;
            settingsManager.setAppGroup(url, group);
            dialog.cancel();
            launcherService.forEachActivity(LauncherActivity::refreshAppList);
        });
        dialog.findViewById(R.id.info).setOnClickListener(view -> {
            dialog.dismiss();
            showWebsiteInfo();
        });

        // Presets
        dialog.findViewById(R.id.presetGoogle).setOnClickListener(view -> urlEdit.setText(R.string.preset_google));
        dialog.findViewById(R.id.presetYoutube).setOnClickListener(view -> urlEdit.setText(R.string.preset_youtube));
        dialog.findViewById(R.id.presetDiscord).setOnClickListener(view -> urlEdit.setText(R.string.preset_discord));
        dialog.findViewById(R.id.presetSpotify).setOnClickListener(view -> urlEdit.setText(R.string.preset_spotify));
        dialog.findViewById(R.id.presetTidal).setOnClickListener(view -> urlEdit.setText(R.string.preset_tidal));
        dialog.findViewById(R.id.presetApkMirror).setOnClickListener(view -> urlEdit.setText(R.string.preset_apkmirror));
        dialog.findViewById(R.id.presetApkPure).setOnClickListener(view -> urlEdit.setText(R.string.preset_apkpure));
    }

    void showWebsiteInfo() {
        AlertDialog subDialog = new LcDialog<>(this, R.layout.dialog_info_websites).show();
        if (subDialog == null) return;
        subDialog.findViewById(R.id.vrOnlyInfo).setVisibility(Platform.isVr() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onLayoutChanged(View ignoredV, int ignoredLeft, int ignoredTop, int right, int bottom, int ignoredOldLeft, int ignoredOldTop, int oldRight, int oldBottom) {
        super.onLayoutChanged(ignoredV, ignoredLeft, ignoredTop, right, bottom, ignoredOldLeft, ignoredOldTop, oldRight, oldBottom);
    }
}
