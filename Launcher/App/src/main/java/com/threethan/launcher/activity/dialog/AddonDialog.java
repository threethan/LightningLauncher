package com.threethan.launcher.activity.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.data.AppData;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.updater.AddonUpdater;

import java.lang.ref.WeakReference;

/*
    AddonDialog

    This class handles the addon manager, AKA shortcut settings
    It handles related popups, and updates buttons according to the state returned by Addon
 */
public class AddonDialog extends BasicDialog<LauncherActivity> {
    private static WeakReference<AddonUpdater> updaterRef;

    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     *
     * @param activity LauncherActivity Context to show the dialog
     */
    public AddonDialog(LauncherActivity activity) {
        super(activity, Platform.isVr(activity) ? R.layout.dialog_addons_vr : R.layout.dialog_addons_tv);
    }

    public AlertDialog show() {
        AlertDialog dialog = super.show();
        if (dialog == null) return null;

        View addonFeed = dialog.findViewById(R.id.addonFeed);
        if (addonFeed!=null) {
            updateAddonButton(context, addonFeed, AddonUpdater.TAG_FEED);
            dialog.findViewById(R.id.disableExplore).setOnClickListener(v -> App.openInfo(context, AppData.EXPLORE_PACKAGE));
            ((TextView) dialog.findViewById(R.id.disableExploreWhy)).setText(
                    App.isPackageEnabled(context, AppData.EXPLORE_PACKAGE) ?
                            R.string.addons_explore_disable_why : R.string.addons_explore_enable_why);
            ((TextView) dialog.findViewById(R.id.disableExploreText)).setText(
                    App.isPackageEnabled(context, AppData.EXPLORE_PACKAGE) ?
                    R.string.addons_explore_disable : R.string.addons_explore_enable);
        }

        View addonLibrary = dialog.findViewById(R.id.addonLibrary);
        if (addonLibrary!=null) updateAddonButton(context, addonLibrary, AddonUpdater.TAG_LIBRARY);

        View addonPeople = dialog.findViewById(R.id.addonPeople);
        if (addonLibrary!=null) updateAddonButton(context, addonPeople, AddonUpdater.TAG_PEOPLE);

        View addonStore = dialog.findViewById(R.id.addonStore);
        if (addonLibrary!=null) updateAddonButton(context, addonStore, AddonUpdater.TAG_STORE);


        View addonAndroidTv = dialog.findViewById(R.id.addonAndroidTv);
        if (addonAndroidTv!=null) updateAddonButton(context, addonAndroidTv, AddonUpdater.TAG_ATV_LM);

        dialog.findViewById(R.id.exitButton).setOnClickListener(v -> dialog.dismiss());
        return dialog;
    }
    public static void updateAddonButton(final Activity a, final View layout, final String tag) {

        final View uninstallButton = layout.findViewById(R.id.addonUninstall);
        final View installButton = layout.findViewById(R.id.addonInstall);
        final View updateButton = layout.findViewById(R.id.addonUpdate);
        final View openButton = layout.findViewById(R.id.addonOpen);
        final View activateButton = layout.findViewById(R.id.addonActivate);
        final View deactivateButton = layout.findViewById(R.id.addonDeactivate);

        View icon = layout.findViewById(R.id.icon);
        if (icon != null) icon.setClipToOutline(true);

        AddonUpdater updater = getUpdater();
        AddonUpdater.Addon addon = updater.getAddon(tag);
        Runnable updateButtonRunnable = new Runnable() {
            @Override
            public void run() {
                uninstallButton.setVisibility(View.GONE);
                installButton.setVisibility(View.GONE);
                updateButton.setVisibility(View.GONE);
                openButton.setVisibility(View.GONE);
                activateButton.setVisibility(View.GONE);
                deactivateButton.setVisibility(View.GONE);

                View[] visibleButtons;
                switch (updater.getAddonState(addon)) {
                    case INSTALLED_APP              -> visibleButtons = new View[]{uninstallButton, openButton};
                    case INSTALLED_SERVICE_ACTIVE   -> visibleButtons = new View[]{deactivateButton, uninstallButton};
                    case INSTALLED_SERVICE_INACTIVE -> visibleButtons = new View[]{activateButton, uninstallButton};
                    case INSTALLED_HAS_UPDATE       -> visibleButtons = new View[]{updateButton, uninstallButton};
                    case NOT_INSTALLED              -> visibleButtons = new View[]{installButton};
                    default                         -> throw new RuntimeException("UNIMPLEMENTED ADDON STATE");
                }
                for (View view : visibleButtons) view.setVisibility(View.VISIBLE);
                layout.postDelayed(this, 100);
            }
        };
        layout.post(updateButtonRunnable);

        uninstallButton.setOnClickListener(v -> getUpdater().uninstallAddon(a, tag));
        installButton.setOnClickListener(v -> getUpdater().installAddon(tag));
        updateButton.setOnClickListener(v -> getUpdater().installAddon(tag));
        activateButton  .setOnClickListener(v -> showAccessibilityDialog());
        deactivateButton.setOnClickListener(v -> showAccessibilityDialog());
        openButton.setOnClickListener(v -> {
            PackageManager pm = a.getPackageManager();
            if (addon != null) a.startActivity(pm.getLaunchIntentForPackage(addon.packageName));
        });
    }
    protected static AddonUpdater getUpdater() {
        AddonUpdater updater = null;
        if (updaterRef != null) updater = updaterRef.get();
        if (updater != null) return updater;
        LauncherActivity.getForegroundInstance();
        updater = new AddonUpdater(LauncherActivity.getForegroundInstance());
        updaterRef = new WeakReference<>(updater);
        return updater;
    }

    protected static void showAccessibilityDialog() {
        Activity a = LauncherActivity.getForegroundInstance();
        AlertDialog subDialog = new BasicDialog<>(a, R.layout.dialog_info_service).show();
        if (subDialog == null) return;
        subDialog.findViewById(R.id.confirm).setOnClickListener(view1 -> {
            // Navigate to accessibility settings
            Intent localIntent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
            localIntent.setPackage("com.android.settings");
            a.startActivity(localIntent);
        });
    }
}
