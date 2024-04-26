package com.threethan.launcher.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.AppData;
import com.threethan.launcher.helper.Dialog;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.updater.AddonUpdater;

import java.lang.ref.WeakReference;
import java.util.Objects;

/*
    AddonDialog

    This class handles the addon manager, AKA shortcut settings
    It handles related popups, and updates buttons according to the state returned by Addon
 */
public abstract class AddonDialog {
    private static WeakReference<AddonUpdater> updaterRef;
    private static WeakReference<LauncherActivity> activityRef;
    public static void showShortcutAddons(LauncherActivity a) {
        AlertDialog dialog = Dialog.build(a, Platform.isVr(a) ? R.layout.dialog_addons_vr : R.layout.dialog_addons_tv);
        if (dialog == null) return;
        activityRef = new WeakReference<>(a);

        View addonFeed = dialog.findViewById(R.id.addonFeed);
        if (addonFeed!=null) {
            updateAddonButton(a, addonFeed, AddonUpdater.TAG_FEED);
            dialog.findViewById(R.id.disableExplore).setOnClickListener(v -> App.openInfo(a, AppData.EXPLORE_PACKAGE));
            ((TextView) dialog.findViewById(R.id.disableExploreWhy)).setText(
                    App.isPackageEnabled(a, AppData.EXPLORE_PACKAGE) ?
                            R.string.addons_explore_disable_why : R.string.addons_explore_enable_why);
            ((TextView) dialog.findViewById(R.id.disableExploreText)).setText(
                    App.isPackageEnabled(a, AppData.EXPLORE_PACKAGE) ?
                    R.string.addons_explore_disable : R.string.addons_explore_enable);
        }

        View addonLibrary = dialog.findViewById(R.id.addonLibrary);
        if (addonLibrary!=null) updateAddonButton(a, addonLibrary, AddonUpdater.TAG_LIBRARY);

        View addonPeople = dialog.findViewById(R.id.addonPeople);
        if (addonLibrary!=null) updateAddonButton(a, addonPeople, AddonUpdater.TAG_PEOPLE);

        View addonStore = dialog.findViewById(R.id.addonStore);
        if (addonLibrary!=null) updateAddonButton(a, addonStore, AddonUpdater.TAG_STORE);


        View addonAndroidTv = dialog.findViewById(R.id.addonAndroidTv);
        if (addonAndroidTv!=null) updateAddonButton(a, addonAndroidTv, AddonUpdater.TAG_ATV_LM);

        dialog.findViewById(R.id.exitButton).setOnClickListener(v -> dialog.dismiss());
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
        if (updater == null) return;
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

        uninstallButton.setOnClickListener(v -> Objects.requireNonNull(getUpdater()).uninstallAddon(a, tag));
        installButton.setOnClickListener(v -> Objects.requireNonNull(getUpdater()).installAddon(tag));
        updateButton.setOnClickListener(v -> Objects.requireNonNull(getUpdater()).installAddon(tag));
        activateButton  .setOnClickListener(v -> showAccessibilityDialog());
        deactivateButton.setOnClickListener(v -> showAccessibilityDialog());
        openButton.setOnClickListener(v -> {
            PackageManager pm = a.getPackageManager();
            if (addon != null) a.startActivity(pm.getLaunchIntentForPackage(addon.packageName));
        });
    }
    @Nullable
    protected static AddonUpdater getUpdater() {
        AddonUpdater updater = null;
        if (updaterRef != null) updater = updaterRef.get();
        if (updater != null) return updater;
        LauncherActivity activity = activityRef.get();
        if (activity != null) {
            updater = new AddonUpdater(activity);
            updaterRef = new WeakReference<>(updater);
            return updater;
        }
        return null;
    }

    protected static void showAccessibilityDialog() {
        Activity a = activityRef.get();
        if (a==null) return;
        AlertDialog subDialog = Dialog.build(a, R.layout.dialog_info_service);
        if (subDialog == null) return;
        subDialog.findViewById(R.id.confirm).setOnClickListener(view1 -> {
            // Navigate to accessibility settings
            Intent localIntent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
            localIntent.setPackage("com.android.settings");
            a.startActivity(localIntent);
        });
    }
}
