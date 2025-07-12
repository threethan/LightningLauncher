package com.threethan.launcher.activity.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launcher.helper.QuestGameTuner;
import com.threethan.launchercore.lib.DelayLib;
import com.threethan.launchercore.metadata.IconLoader;
import com.threethan.launchercore.lib.ImageLib;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.App;
import com.threethan.launchercore.util.CustomDialog;
import com.threethan.launchercore.util.Platform;
import com.threethan.launchercore.view.LcToolTipHelper;

import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides the dialog which appears when pressing the three-dots icon on an app,
 * or when long-pressing an app in edit mode
 */
public class AppDetailsDialog extends BasicDialog<LauncherActivity> {
    private static File customIconFile;
    private static ApplicationInfo imageApp;
    private final ApplicationInfo app;

    /**
     * Constructs a new GroupDetailsDialog. Make sure to call .show()!
     * @param launcherActivity Parent activity
     * @param app App to show details of
     */
    public AppDetailsDialog(LauncherActivity launcherActivity, ApplicationInfo app) {
        super(launcherActivity, R.layout.dialog_details_app);
        this.app = app;
    }

    @SuppressLint("SetTextI18n")
    public AlertDialog show() {
        // Set View
        AlertDialog dialog = super.show();
        if (dialog == null) return null;
        // Package Name
        ((TextView) dialog.findViewById(R.id.packageName)).setText(app.packageName);

        PackageInfo packageInfo;
        try {
            packageInfo = a.getPackageManager().getPackageInfo(app.packageName, 0);
            ((TextView) dialog.findViewById(R.id.packageVersion)).setText(packageInfo.versionName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                View pnv = dialog.findViewById(R.id.packageNameAndVersion);
                LcToolTipHelper.init(pnv, packageInfo.packageName
                        + "\n" + packageInfo.versionName);
            }
        } catch (PackageManager.NameNotFoundException ignored) {}
        // Info Action
        dialog.findViewById(R.id.info).setOnClickListener(view
                -> AppExt.openInfo(a, app.packageName));
        dialog.findViewById(R.id.uninstall).setOnClickListener(view -> {
            AppExt.uninstall(app.packageName); dialog.dismiss();});

        // Launch Mode Toggle
        final View resetIconButton = dialog.findViewById(R.id.resetIcon);
        resetIconButton.setVisibility(
                IconLoader.iconCustomFileForApp(app).exists() ? View.VISIBLE : View.GONE
        );
        final View tuningButton = dialog.findViewById(R.id.tuningButton);

        final Spinner launchSizeSpinner = dialog.findViewById(R.id.launchSizeSpinner);
        final Spinner launchBrowserSpinner = dialog.findViewById(R.id.launchBrowserSpinner);

        // Load Icon
        ImageView iconImageView = dialog.findViewById(R.id.appIcon);
        IconLoader.loadIcon(app, drawable -> {
            if (LauncherActivity.getForegroundInstance() != null)
                LauncherActivity.getForegroundInstance().runOnUiThread(() ->
                        iconImageView.setImageDrawable(drawable)
                );
        });

        iconImageView.setOnClickListener(iconPickerView -> {
            customIconFile = IconLoader.iconCustomFileForApp(app);
            if (customIconFile.exists()) //noinspection ResultOfMethodCallIgnored
                customIconFile.delete();
            a.setSelectedIconImage(iconImageView);
            imageApp = app;
            a.showFilePicker(LauncherActivity.FilePickerTarget.ICON);
            // Show the reset button (delayed so it doesn't appear before the image picker)
            DelayLib.delayed(() ->
                    a.runOnUiThread(() -> resetIconButton.setVisibility(View.VISIBLE)));
        });

        App.Type appType = AppExt.getType(app);
        dialog.findViewById(R.id.info).setVisibility(app.packageName.contains("://")
                && !PlatformExt.infoOverrides.containsKey(app.packageName)
                ? View.GONE : View.VISIBLE);
        dialog.findViewById(R.id.uninstall).setVisibility(appType == App.Type.PANEL
                && app.packageName.contains("://") || (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (appType != App.Type.WEB && !PlatformExt.canUninstall())
                ? View.GONE : View.VISIBLE);
        resetIconButton.setOnClickListener(view -> Compat.resetIcon(app, d
                -> a.runOnUiThread(() -> {
                    iconImageView.setImageDrawable(d);
                    view.setVisibility(View.GONE);
                    if (a.getAppAdapter() != null) a.getAppAdapter().notifyItemChanged(app);
        })));
        if (appType == App.Type.VR || appType == App.Type.PANEL
                || Platform.isTv()) {
            // VR apps MUST launch out, so just hide the option and replace it with another
            // Also hide it on TV where it is useless
            if (appType == App.Type.VR && Platform.isQuest()) {
                tuningButton.setVisibility(View.VISIBLE);
                tuningButton.setOnClickListener(v -> QuestGameTuner.tuneApp(app.packageName));
            }
            launchSizeSpinner.setVisibility(View.GONE);
        } else {
            tuningButton.setVisibility(View.GONE);


            // Browser selection spinner
            if (appType == App.Type.WEB) {
                final String launchBrowserKey = Settings.KEY_LAUNCH_BROWSER + app.packageName;
                final int launchBrowserSelection = a.dataStoreEditor.getInt(
                        launchBrowserKey,
                        SettingsManager.getAppLaunchOut(app.packageName) ? 0 : 1);
                initSpinner(launchBrowserSpinner,
                        Platform.isQuest()
                                ? R.array.advanced_launch_browsers_quest
                                : R.array.advanced_launch_browsers,
                        p -> a.dataStoreEditor.putInt(launchBrowserKey, p),
                        launchBrowserSelection);
                launchBrowserSpinner.setVisibility(View.VISIBLE);
            } else if (Platform.isVr() && !appType.equals(App.Type.UTILITY)) {
                final String launchSizeKey = Settings.KEY_LAUNCH_SIZE + app.packageName;
                final int launchSizeSelection = a.dataStoreEditor.getInt(
                        launchSizeKey,
                        SettingsManager.getAppLaunchOut(app.packageName) ? 0 : 1);
                AtomicBoolean isInit = new AtomicBoolean(true);
                initSpinner(launchSizeSpinner, R.array.advanced_launch_sizes, p -> {
                    a.dataStoreEditor.putInt(launchSizeKey, p);
                    if (!isInit.get() && !a.dataStoreEditor.getBoolean("HAS_SEEN_3LS_PROMPT", false)) {
                        new CustomDialog.Builder(a)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.custom_window_size_message)
                                .setPositiveButton(R.string.understood, (d, w)
                                        -> {
                                    d.dismiss();
                                    a.dataStoreEditor.putBoolean("HAS_SEEN_3LS_PROMPT", true);
                                })
                                .show();
                    }
                    isInit.set(false);
                },
                        launchSizeSelection
                );
                launchSizeSpinner.setVisibility(View.VISIBLE);
            }
        }
        if (Platform.isQuest()) dialog.findViewById(R.id.charts).setOnClickListener(v
                    -> PlaytimeHelper.openFor(app.packageName));
        else dialog.findViewById(R.id.charts).setVisibility(View.GONE);

        // Show/hide button
        final View showButton = dialog.findViewById(R.id.show);
        final View hideButton = dialog.findViewById(R.id.hide);

        AtomicReference<String> unhideGroup = new AtomicReference<>(Settings.HIDDEN_GROUP);
        SettingsManager.getGroupAppsMap().entrySet().stream().filter(e ->
                e.getValue().contains(app.packageName)).findFirst().ifPresent(e -> unhideGroup.set(e.getKey()));

        if (Objects.equals(unhideGroup.get(), Settings.HIDDEN_GROUP))
            unhideGroup.set(SettingsManager.getDefaultGroupFor(AppExt.getType(app)));
        if (Objects.equals(unhideGroup.get(), Settings.HIDDEN_GROUP))
            try {
                unhideGroup.set((String) SettingsManager.getAppGroups().toArray()[0]);
            } catch (AssertionError | IndexOutOfBoundsException ignored) {
                unhideGroup.set(Settings.HIDDEN_GROUP);
                BasicDialog.toast("Could not find a group to unhide app to!");
            }
        String finalUnhideGroup = unhideGroup.get();

        Set<String> hg = SettingsManager.getGroupAppsMap().get(SettingsManager.HIDDEN_GROUP);
        boolean amHidden = hg != null && hg.contains(app.packageName);
        showButton.setVisibility( amHidden ? View.VISIBLE : View.GONE);
        hideButton.setVisibility(!amHidden ? View.VISIBLE : View.GONE);
        showButton.setOnClickListener(v -> {
            a.settingsManager.setAppGroup(app.packageName, finalUnhideGroup);
            boolean nowHidden = hg != null && hg.contains(app.packageName);
            showButton.setVisibility( nowHidden ? View.VISIBLE : View.GONE);
            hideButton.setVisibility(!nowHidden ? View.VISIBLE : View.GONE);
            BasicDialog.toast(a.getString(R.string.moved_shown), finalUnhideGroup, false);
            a.launcherService.forEachActivity(LauncherActivity::refreshAppList);

        });
        hideButton.setOnClickListener(v -> {
            a.settingsManager.setAppGroup(app.packageName, Settings.HIDDEN_GROUP);
            boolean nowHidden = hg != null && hg.contains(app.packageName);
            showButton.setVisibility( nowHidden ? View.VISIBLE : View.GONE);
            hideButton.setVisibility(!nowHidden ? View.VISIBLE : View.GONE);
            BasicDialog.toast(a.getString(R.string.moved_hidden),
                    a.getString(R.string.moved_hidden_bold), false);
            a.launcherService.forEachActivity(LauncherActivity::refreshAppList);
        });

        boolean isBanner = AppExt.isBanner(app);
        final View dispIconButton = dialog.findViewById(R.id.dispIcon);
        final View dispBannerButton = dialog.findViewById(R.id.dispWide);
        dispIconButton.setVisibility( isBanner ? View.VISIBLE : View.GONE);
        dispBannerButton.setVisibility(!isBanner ? View.VISIBLE : View.GONE);
        dispIconButton.setOnClickListener(v -> {
            SettingsManager.setAppBannerOverride(app, false);
            a.launcherService.forEachActivity(LauncherActivity::resetAdapters);
            iconImageView.getLayoutParams().width = a.dp(83);
            dispBannerButton.setVisibility(View.VISIBLE);
            dispIconButton.setVisibility(View.GONE);
            resetIconButton.callOnClick();
        });
        dispBannerButton.setOnClickListener(v -> {
            SettingsManager.setAppBannerOverride(app, true);
            SettingsManager.sortableLabelCache.clear();
            a.launcherService.forEachActivity(LauncherActivity::resetAdapters);
            iconImageView.getLayoutParams().width = a.dp(150);
            dispBannerButton.setVisibility(View.GONE);
            dispIconButton.setVisibility(View.VISIBLE);
            resetIconButton.callOnClick();
        });
        iconImageView.setClipToOutline(true);
        iconImageView.getLayoutParams().width = a.dp(isBanner ? 150 : 83);

        // Set Label (don't show star)
        final String[] label = {""};
        final EditText appNameEditText = dialog.findViewById(R.id.appLabel);
        SettingsManager.getAppLabel(app, l -> {
            label[0] = l;
            appNameEditText.setText(StringLib.withoutNew(StringLib.withoutStar(label[0])));
        });
        // Star (actually changes label)
        final ImageView starButton = dialog.findViewById(R.id.star);
        final boolean[] isStarred = {StringLib.hasStar(label[0])};
        starButton.setImageResource(isStarred[0] ? R.drawable.ic_star_on : R.drawable.ic_star_off);
        starButton.setOnClickListener((view) -> {
            isStarred[0] = !isStarred[0];
            starButton.setImageResource(isStarred[0] ? R.drawable.ic_star_on : R.drawable.ic_star_off);
        });

        // Save Label & Reload on Confirm
        dialog.findViewById(R.id.confirm).setOnClickListener(view -> {
            String newLabel = StringLib.setStarred(appNameEditText.getText().toString(), isStarred[0]);
            if (!newLabel.equals(SettingsManager.getAppLabel(app))) {
                SettingsManager.setAppLabel(app, newLabel);
                a.launcherService.forEachActivity(a -> {
                    if (a.getAppAdapter() != null) {
                        a.getAppAdapter().notifyItemChanged(app);
                        a.refreshAppList();
                    }
                });
            }
            dialog.dismiss();
        });
        return dialog;
    }
    public static void onImageSelected(@NonNull Bitmap bitmap,
                                       ImageView selectedImageView, LauncherActivity launcherActivity) {
        bitmap = ImageLib.getResizedBitmap(bitmap, IconLoader.ICON_HEIGHT);
        ImageLib.saveBitmap(bitmap, customIconFile);
        selectedImageView.setImageBitmap(bitmap);

        IconLoader.cachedIcons.remove(IconLoader.cacheName(imageApp));
        launcherActivity.launcherService.forEachActivity(a -> {
            if (a.getAppAdapter() != null) a.getAppAdapter().notifyItemChanged(imageApp);
        });
    }
}
