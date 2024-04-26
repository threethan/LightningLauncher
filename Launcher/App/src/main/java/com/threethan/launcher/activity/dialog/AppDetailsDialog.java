package com.threethan.launcher.activity.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.Icon;
import com.threethan.launcher.helper.Launch;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.lib.ImageLib;
import com.threethan.launcher.lib.StringLib;

import java.io.File;
import java.util.Objects;

/**
 * Provides the dialog which appears when pressing the three-dots icon on an app,
 * or when long-pressing an app in edit mode
 */
public class AppDetailsDialog extends BasicDialog<LauncherActivity> {
    private static File customIconFile;
    private static ApplicationInfo imageApp;
    private final ApplicationInfo currentApp;

    /**
     * Constructs a new GroupDetailsDialog. Make sure to call .show()!
     * @param launcherActivity Parent activity
     * @param app App to show details of
     */
    public AppDetailsDialog(LauncherActivity launcherActivity, ApplicationInfo app) {
        super(launcherActivity, R.layout.dialog_details_app);
        this.currentApp = app;
    }

    @SuppressLint("SetTextI18n")
    public AlertDialog show() {
        // Set View
        AlertDialog dialog = super.show();
        if (dialog == null) return null;
        // Package Name
        ((TextView) dialog.findViewById(R.id.packageName)).setText(currentApp.packageName);

        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(currentApp.packageName, 0);
            ((TextView) dialog.findViewById(R.id.packageVersion)).setText("v"+packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {}
        // Info Action
        dialog.findViewById(R.id.info).setOnClickListener(view -> App.openInfo(context, currentApp.packageName));
        dialog.findViewById(R.id.uninstall).setOnClickListener(view -> {
            App.uninstall(currentApp.packageName); dialog.dismiss();});

        // Launch Mode Toggle
        @SuppressLint("UseSwitchCompatOrMaterialCode")
        final Switch launchModeSwitch = dialog.findViewById(R.id.launchModeSwitch);
        final View launchOutButton = dialog.findViewById(R.id.launchOut);
        final View launchModeSection = dialog.findViewById(R.id.launchModeSection);
        final View refreshIconButton = dialog.findViewById(R.id.refreshIconButton);

        // Launch Mode Selection
        final View launchSizeSpinner = dialog.findViewById(R.id.launchSizeSpinner);
        final TextView launchSizeSpinnerText = dialog.findViewById(R.id.launchSizeSpinnerText);
        // Launch Browser Selection
        final View launchBrowserSpinner = dialog.findViewById(R.id.launchBrowserSpinner);
        final TextView launchBrowserSpinnerText = dialog.findViewById(R.id.launchBrowserSpinnerText);

        // Load Icon
        ImageView iconImageView = dialog.findViewById(R.id.appIcon);
        iconImageView.setImageDrawable(Icon.loadIcon(context, currentApp, null));

        iconImageView.setClipToOutline(true);
        if (App.isBanner(currentApp)) iconImageView.getLayoutParams().width = context.dp(150);

        iconImageView.setOnClickListener(iconPickerView -> {
            customIconFile = Icon.iconCustomFileForApp(currentApp);
            if (customIconFile.exists()) //noinspection ResultOfMethodCallIgnored
                customIconFile.delete();
            context.setSelectedIconImage(iconImageView);

            imageApp = currentApp;
            context.showImagePicker(LauncherActivity.ImagePickerTarget.ICON);
        });

        App.Type appType = App.getType(currentApp);
        dialog.findViewById(R.id.info).setVisibility(currentApp.packageName.contains("://")
                ? View.GONE : View.VISIBLE);
        if (appType == App.Type.TYPE_VR || appType == App.Type.TYPE_PANEL
                || Platform.isTv(context)) {
            // VR apps MUST launch out, so just hide the option and replace it with another
            // Also hide it on TV where it is useless
            launchModeSection.setVisibility(View.GONE);
            refreshIconButton.setVisibility(View.VISIBLE);
            launchOutButton.setVisibility(View.GONE);

            refreshIconButton.setOnClickListener(view -> Icon.reloadIcon(context, currentApp, iconImageView));
        } else {
            launchModeSection.setVisibility(View.VISIBLE);
            refreshIconButton.setVisibility(View.GONE);

            launchOutButton.setVisibility(View.VISIBLE);
            launchOutButton.setOnClickListener((view) -> {
                final boolean prevLaunchOut = SettingsManager.getAppLaunchOut(currentApp.packageName);
                SettingsManager.setAppLaunchOut(currentApp.packageName, true);
                Launch.launchApp(context, currentApp);
                SettingsManager.setAppLaunchOut(currentApp.packageName, prevLaunchOut);
            });

            // Normal size settings
            launchModeSwitch.setChecked(SettingsManager.getAppLaunchOut(currentApp.packageName));
            launchModeSwitch.setOnCheckedChangeListener((sw, value)
                    -> SettingsManager.setAppLaunchOut(currentApp.packageName, value));
            launchModeSwitch.setVisibility(Platform.isVr(context) ? View.VISIBLE : View.GONE);

            // Browser selection spinner
            if (appType == App.Type.TYPE_WEB) {
                launchBrowserSpinner.setVisibility(View.VISIBLE);
                launchModeSection.setVisibility(View.GONE);

                final String launchBrowserKey = Settings.KEY_LAUNCH_BROWSER + currentApp.packageName;
                final int[] launchBrowserSelection = {context.dataStoreEditor.getInt(
                        launchBrowserKey,
                        SettingsManager.getDefaultBrowser()
                )};

                launchBrowserSpinnerText.setText(Settings.launchBrowserStrings[launchBrowserSelection[0]]);
                launchBrowserSpinner.setOnClickListener((view) -> {
                    // Cycle selection
                    int index = launchBrowserSelection[0];
                    index = (index + 1) % Settings.launchBrowserStrings.length;
                    // Skip quest browser if not on quest
                    if (Settings.launchBrowserStrings[index] == R.string.browser_quest
                        && !Platform.isQuest(context)) index++;
                    // Update text & store setting
                    final int stringRes = Settings.launchBrowserStrings[index];
                    launchBrowserSpinnerText.setText(stringRes);
                    context.dataStoreEditor.putInt(launchBrowserKey, index);
                    launchBrowserSelection[0] = index;
                });
            }
            // Advanced size settings
            else if (SettingsManager.getShowAdvancedSizeOptions(context)) {
                launchSizeSpinner.setVisibility(View.VISIBLE);
                launchModeSection.setVisibility(View.GONE);
                final String launchSizeKey = Settings.KEY_LAUNCH_SIZE + currentApp.packageName;
                final int[] launchSizeSelection = {context.dataStoreEditor.getInt(
                        launchSizeKey,
                        SettingsManager.getAppLaunchOut(currentApp.packageName) ? 0 : 1)};

                launchSizeSpinnerText.setText(Settings.launchSizeStrings[launchSizeSelection[0]]);
                launchSizeSpinner.setOnClickListener((view) -> {
                    launchSizeSelection[0] = (launchSizeSelection[0] + 1) % Settings.launchSizeStrings.length;
                    launchSizeSpinnerText.setText(Settings.launchSizeStrings[launchSizeSelection[0]]);
                    context.dataStoreEditor.putInt(launchSizeKey, launchSizeSelection[0]);
                    SettingsManager.setAppLaunchOut(currentApp.packageName, launchSizeSelection[0] != 0);
                });
            }
        }

        // Show/hide button
        final View showButton = dialog.findViewById(R.id.show);
        final View hideButton = dialog.findViewById(R.id.hide);
        String unhideGroup = SettingsManager.getAppGroupMap().get(currentApp.packageName);
        if (Objects.equals(unhideGroup, Settings.HIDDEN_GROUP))
            unhideGroup = App.getDefaultGroupFor(App.getType(currentApp));
        if (Objects.equals(unhideGroup, Settings.HIDDEN_GROUP))
            try {
                unhideGroup = (String) SettingsManager.getAppGroups().toArray()[0];
            } catch (AssertionError | IndexOutOfBoundsException ignored) {
                unhideGroup = Settings.HIDDEN_GROUP;
                BasicDialog.toast("Could not find a group to unhide app to!");
            }
        String finalUnhideGroup = unhideGroup;

        boolean amHidden = Objects.equals(SettingsManager.getAppGroupMap()
                .get(currentApp.packageName), Settings.HIDDEN_GROUP);
        showButton.setVisibility( amHidden ? View.VISIBLE : View.GONE);
        hideButton.setVisibility(!amHidden ? View.VISIBLE : View.GONE);
        showButton.setOnClickListener(v -> {
            context.settingsManager.setAppGroup(currentApp.packageName,
                    finalUnhideGroup);
            boolean nowHidden = Objects.equals(SettingsManager.getAppGroupMap()
                    .get(currentApp.packageName), Settings.HIDDEN_GROUP);
            showButton.setVisibility( nowHidden ? View.VISIBLE : View.GONE);
            hideButton.setVisibility(!nowHidden ? View.VISIBLE : View.GONE);
            BasicDialog.toast(context.getString(R.string.moved_shown), finalUnhideGroup, false);
            context.launcherService.forEachActivity(LauncherActivity::refreshAppList);

        });
        hideButton.setOnClickListener(v -> {
            context.settingsManager.setAppGroup(currentApp.packageName,
                    Settings.HIDDEN_GROUP);
            boolean nowHidden = Objects.equals(SettingsManager.getAppGroupMap()
                    .get(currentApp.packageName), Settings.HIDDEN_GROUP);
            showButton.setVisibility( nowHidden ? View.VISIBLE : View.GONE);
            hideButton.setVisibility(!nowHidden ? View.VISIBLE : View.GONE);
            BasicDialog.toast(context.getString(R.string.moved_hidden),
                    context.getString(R.string.moved_hidden_bold), false);
            context.launcherService.forEachActivity(LauncherActivity::refreshAppList);
        });

        // Set Label (don't show star)
        String label = SettingsManager.getAppLabel(currentApp);
        final EditText appNameEditText = dialog.findViewById(R.id.appLabel);
        appNameEditText.setText(StringLib.withoutStar(label));
        // Star (actually changes label)
        final ImageView starButton = dialog.findViewById(R.id.star);
        final boolean[] isStarred = {StringLib.hasStar(label)};
        starButton.setImageResource(isStarred[0] ? R.drawable.ic_star_on : R.drawable.ic_star_off);
        starButton.setOnClickListener((view) -> {
            isStarred[0] = !isStarred[0];
            starButton.setImageResource(isStarred[0] ? R.drawable.ic_star_on : R.drawable.ic_star_off);
        });
        // Save Label & Reload on Confirm
        dialog.findViewById(R.id.confirm).setOnClickListener(view -> {
            SettingsManager.setAppLabel(currentApp, StringLib.setStarred(appNameEditText.getText().toString(), isStarred[0]));
            context.launcherService.forEachActivity(a -> {
                if (a.getAppAdapter() != null) {
                    a.getAppAdapter().notifyItemChanged(currentApp);
                    a.refreshAppList();
                }
            });
            dialog.dismiss();
        });
        return dialog;
    }
    public static void onImageSelected(@NonNull Bitmap bitmap,
                                       ImageView selectedImageView, LauncherActivity launcherActivity) {
        bitmap = ImageLib.getResizedBitmap(bitmap, 450);
        ImageLib.saveBitmap(bitmap, customIconFile);
        selectedImageView.setImageBitmap(bitmap);

        Icon.cachedIcons.remove(Icon.cacheName(imageApp));
        launcherActivity.launcherService.forEachActivity(a -> {
            if (a.getAppAdapter() != null) a.getAppAdapter().notifyItemChanged(imageApp);
        });
    }
}
