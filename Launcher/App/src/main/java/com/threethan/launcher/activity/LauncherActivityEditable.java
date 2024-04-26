package com.threethan.launcher.activity;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.adapter.AppsAdapter;
import com.threethan.launcher.activity.adapter.GroupsAdapter;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.activity.dialog.BasicDialog;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.lib.StringLib;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.activity.view.EditTextWatched;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import eightbitlab.com.blurview.BlurView;

/**
    The class handles the additional interface elements and properties related to edit mode.
    This includes the bottom bar & dialog for adding websites, but not the dialogs to edit an
    individual app or group.
 */

public class LauncherActivityEditable extends LauncherActivity {
    @Nullable
    protected Boolean editMode = null;

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
    public HashSet<String> currentSelectedApps = new ConnectedHashSet();
    // Startup
    @Override
    protected void init() {
        super.init();
        View addWebsiteButton = rootView.findViewById(R.id.addWebsite);
        addWebsiteButton.setOnClickListener(view -> addWebsite(this));
        View stopEditingButton = rootView.findViewById(R.id.stopEditing);
        stopEditingButton.setOnClickListener(view -> setEditMode(false));
    }

    @Override
    public void refreshInterface() {
        dataStoreEditor = new DataStoreEditor(this);

        if (editMode == null) editMode = dataStoreEditor.getBoolean(Settings.KEY_EDIT_MODE, false);

        super.refreshInterface();

        final View editFooter = rootView.findViewById(R.id.editFooter);
        if (editMode) {
            // Edit bar theming and actions
            editFooter.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00000000")));

            final TextView selectionHintText = rootView.findViewById(R.id.selectionHintText);
            final ImageView uninstallButton = rootView.findViewById(R.id.uninstallBulk);

            for (TextView textView: new TextView[]{selectionHintText, rootView.findViewById(R.id.addWebsite), rootView.findViewById(R.id.stopEditing)}) {
                textView.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(darkMode ? "#80000000" : "#FFFFFF")));
                textView.setTextColor(Color.parseColor(darkMode ? "#FFFFFF" : "#000000"));
            }
            selectionHintText  .setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(darkMode ? "#80000000" : "#FFFFFF")));
            uninstallButton.setImageTintList(ColorStateList.valueOf(Color.parseColor(darkMode ? "#FFFFFF" : "#3a3a3c")));

            selectionHintText.setOnClickListener((view) -> {
                if (currentSelectedApps.isEmpty()) {
                    final AppsAdapter adapter = getAppAdapter();
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
                    final AppsAdapter adapter = getAppAdapter();
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
                    mainView.postDelayed(() -> App.uninstall(currentSelectedApp), delay);
                    if (!App.isWebsite(currentSelectedApp)) delay += 1000;
                }
            });
        }
        if (editFooter.getVisibility() == View.GONE && editMode) {
            editFooter.setTranslationY(100f);
            editFooter.setVisibility(View.VISIBLE);
        }
        ObjectAnimator aF = ObjectAnimator.ofFloat(editFooter, "TranslationY", editMode ?0f:100f);
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
    public void clickGroup(int position) {
        lastSelectedGroup = position;
        final List<String> groupsSorted = settingsManager.getAppGroupsSorted(false);

        // If the new group button was selected, create and select a new group
        if (position >= groupsSorted.size()) {
            final String ignored = settingsManager.addGroup();
            super.clickGroup(position-1); //Auto-move selection and select new group
            refreshInterface();
            postDelayed(() -> clickGroup(position-1), 500); //Auto-move selection
            return;
        }
        final String group = groupsSorted.get(position);

        // Move apps if any are selected
        if (!currentSelectedApps.isEmpty()) {
            GroupsAdapter groupsAdapter = getGroupAdapter();
            if (groupsAdapter != null)
                for (String app : currentSelectedApps)
                    groupsAdapter.setGroup(app, group);

            TextView selectionHintText = rootView.findViewById(R.id.selectionHintText);
            selectionHintText.setText( currentSelectedApps.size()==1 ?
                    getString(R.string.selection_moved_single, group) :
                    getString(R.string.selection_moved_multiple, currentSelectedApps.size(), group)
            );
            rootView.findViewById(R.id.uninstallBulk).setVisibility(View.GONE);
            selectionHintText.postDelayed(this::updateSelectionHint, 2000);

            currentSelectedApps.clear();

            SettingsManager.writeGroupsAndSort();
            refreshInterface();
        } else super.clickGroup(position);
    }

    // Function overrides
    @Override
    public void setEditMode(boolean value) {
        editMode = value;
        if (!editMode) currentSelectedApps.clear();
        if (dataStoreEditor == null) return;
        dataStoreEditor.putBoolean(Settings.KEY_EDIT_MODE, editMode);
        final View focused = getCurrentFocus();
        refreshInterface();
        if (focused != null) {
            focused.clearFocus();
            focused.post(focused::requestFocus);
        }
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
            for (String appPackage : currentSelectedApps)
                if (!packages.contains(appPackage) && !webApps.contains(appPackage))
                    currentSelectedApps.remove(appPackage);
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
        uninstallButton.setVisibility(currentSelectedApps.isEmpty() ? View.GONE : View.VISIBLE);

        final int size = currentSelectedApps.size();
        runOnUiThread(() -> {
            if (size == 0)      selectionHintText.setText(R.string.selection_hint_none);
            else if (size == 1) selectionHintText.setText(R.string.selection_hint_single);
            else selectionHintText.setText(getString(R.string.selection_hint_multiple, size));
        });
    }

    public void addWebsite(Context context) {
        
        AlertDialog dialog = new BasicDialog<>(this, R.layout.dialog_add_website).show();

        // Set group to (one of) selected
        String group;
        final ArrayList<String> appGroupsSorted = settingsManager.getAppGroupsSorted(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && !appGroupsSorted.isEmpty())
            group = appGroupsSorted.get(0);
        else group = App.getDefaultGroupFor(App.Type.TYPE_PHONE);

        if (dialog == null) return;

        dialog.findViewById(R.id.cancel).setOnClickListener(view -> dialog.cancel());
        ((TextView) dialog.findViewById(R.id.addText)).setText(getString(R.string.add_website_group, group));
        EditTextWatched urlEdit = dialog.findViewById(R.id.appUrl);
        urlEdit.post(urlEdit::requestFocus);

        TextView badUrl  = dialog.findViewById(R.id.badUrl);
        TextView usedUrl = dialog.findViewById(R.id.usedUrl);
        urlEdit.setOnEdited(url -> {
            if (StringLib.isInvalidUrl(url)) url = "https://" + url;
            badUrl .setVisibility(StringLib.isInvalidUrl(url)
                    ? View.VISIBLE : View.GONE);

            String foundGroup = Platform.findWebsite(dataStoreEditor, url);
            usedUrl.setVisibility(foundGroup != null
                    ? View.VISIBLE : View.GONE);
            if (foundGroup != null)
                usedUrl.setText(context.getString(R.string.add_website_used_url, foundGroup));
        });

        dialog.findViewById(R.id.confirm).setOnClickListener(view -> {
            String url  = urlEdit.getText().toString().toLowerCase();
            if (StringLib.isInvalidUrl(url)) url = "https://" + url;
            if (StringLib.isInvalidUrl(url)) return;
            if (Platform.findWebsite(dataStoreEditor, url) != null) return;
            Platform.addWebsite(dataStoreEditor, url);
            settingsManager.setAppGroup(StringLib.fixUrl(url), group);
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
        AlertDialog subDialog = new BasicDialog<>(this, R.layout.dialog_info_websites).show();
        if (subDialog == null) return;
        subDialog.findViewById(R.id.vrOnlyInfo).setVisibility(Platform.isVr(this) ? View.VISIBLE : View.GONE);
        subDialog.findViewById(R.id.confirm).setOnClickListener(view -> {
            dataStoreEditor.putBoolean(Settings.KEY_SEEN_WEBSITE_POPUP, true);
            addWebsite(this);
            subDialog.dismiss();
        });
    }

    @Override
    public void updateToolBars() {
        super.updateToolBars();
        if (!isEditing()) return;

        BlurView blurViewE = rootView.findViewById(R.id.
                editFooter);
        blurViewE.setOverlayColor(Color.parseColor(darkMode ? "#2A000000" : "#45FFFFFF"));

        initBlurView(blurViewE);

        blurViewE.setActivated(false);
        blurViewE.setActivated(true);
    }
}
