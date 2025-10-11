package com.threethan.launcher.activity.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Switch;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launcher.updater.AddonUpdater;
import com.threethan.launchercore.util.Platform;

import java.lang.ref.WeakReference;

/**
    AddonDialog

    This class handles the addon manager, AKA shortcut settings
    It handles related popups, and updates buttons according to the state returned by Addon
 */
public class AddonManagerDialog extends BasicDialog<LauncherActivity> {
    private static WeakReference<AddonUpdater> updaterRef;

    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     *
     * @param activity LauncherActivity Context to show the dialog
     */
    public AddonManagerDialog(LauncherActivity activity) {
        super(activity, determineLayoutRes(activity));
    }

    /** Determine the layout resource to use on current platform */
    private static int determineLayoutRes(LauncherActivity activity) {
        if (Platform.isVr()) {
            if (
                    Platform.getVrOsVersion() > 76 &&
                            Platform.getVrOsVersion() < 82 && // [SPECULATION] Navigator will be default by v82
                            !activity.dataStoreEditor.getBoolean(Settings.KEY_ADDONS_VR_TYPE_KNOWN, false)
            ) {
                return R.layout.dialog_addons_vr_indeterminate;
            } else {
                boolean defaultHasNavigator = Platform.getVrOsVersion() > 76;
                boolean hasNavigator = activity.dataStoreEditor.getBoolean(
                        Settings.KEY_ADDONS_VR_HAS_NAVIGATOR,
                        defaultHasNavigator
                );
                return hasNavigator
                        ? R.layout.dialog_addons_vr_navigator
                        : R.layout.dialog_addons_vr_dock;
            }
        } else {
            return R.layout.dialog_addons_tv;
        }
    }

    public AlertDialog show() {
        if (Platform.isVr() && !BasicDialog.validateVariantWithNotify()) return null;
        AlertDialog dialog = super.show();
        if (dialog == null) return null;

        View addonFeed = dialog.findViewById(R.id.addonFeed);
        if (addonFeed!=null) updateAddonButton(a, addonFeed, AddonUpdater.TAG_FEED);

        View addonLibrary = dialog.findViewById(R.id.addonLibrary);
        if (addonLibrary!=null) updateAddonButton(a, addonLibrary, AddonUpdater.TAG_LIBRARY);

        View addonPeople = dialog.findViewById(R.id.addonPeople);
        if (addonPeople!=null) updateAddonButton(a, addonPeople, AddonUpdater.TAG_PEOPLE);

        View addonStore = dialog.findViewById(R.id.addonStore);
        if (addonStore!=null) updateAddonButton(a, addonStore, AddonUpdater.TAG_STORE);

        View addonNavigator = dialog.findViewById(R.id.addonNavigator);
        if (addonNavigator!=null) updateAddonButton(a, addonNavigator, AddonUpdater.TAG_NAVIGATOR);

        View addDockButton = dialog.findViewById(R.id.addToDockButton);
        if (addDockButton != null) addDockButton.setOnClickListener(v -> showDockDialog());
        dialog.findViewById(R.id.exitButton).setOnClickListener(v -> dialog.dismiss());

        View variantSwitchContainer = dialog.findViewById(R.id.variantSwitchContainer);
        if (variantSwitchContainer != null) {
            boolean allowShortcuts =
                    a.dataStoreEditor.getBoolean(Settings.KEY_ALLOW_SHORTCUTS, Settings.DEFAULT_ALLOW_SHORTCUTS);

            if (VariantHelper.hasVariants(a)) {
                variantSwitchContainer.setVisibility(View.VISIBLE);

                Switch variantSwitch = dialog.findViewById(R.id.variantSwitch);
                variantSwitch.setChecked(allowShortcuts);

                dialog.findViewById(R.id.variantSwitch).setOnClickListener(v
                        -> a.dataStoreEditor.putBoolean(Settings.KEY_ALLOW_SHORTCUTS, variantSwitch.isChecked()));
            } else {
                variantSwitchContainer.setVisibility(View.GONE);
                a.dataStoreEditor.putBoolean(Settings.KEY_ALLOW_SHORTCUTS, Settings.DEFAULT_ALLOW_SHORTCUTS);
            }
        }

        if (PlatformExt.isOldVrOs()) {
            View addToDockSuggest = dialog.findViewById(R.id.addToDockSuggest);
            if (addToDockSuggest != null) addToDockSuggest.setVisibility(View.GONE);
        } if (Platform.getVrOsVersion() < 78) {
            View addonNavigatorSuggest = dialog.findViewById(R.id.addonNavigatorSuggest);
            if (addonNavigatorSuggest != null) addonNavigatorSuggest.setVisibility(View.GONE);
        }

        View navigatorGotoView = dialog.findViewById(R.id.addonNavigatorGotoButton);
        if (navigatorGotoView != null) navigatorGotoView.setOnClickListener(v -> {
            a.dataStoreEditor.putValue(Settings.KEY_ADDONS_VR_TYPE_KNOWN, true, true);
            a.dataStoreEditor.putValue(Settings.KEY_ADDONS_VR_HAS_NAVIGATOR, true, true);
            dialog.dismiss();
            new AddonManagerDialog(a).show();
        });

        View dockGotoButton    = dialog.findViewById(R.id.addonDockGotoButton);
        if (dockGotoButton    != null) dockGotoButton   .setOnClickListener(v -> {
            a.dataStoreEditor.putValue(Settings.KEY_ADDONS_VR_TYPE_KNOWN, true, true);
            a.dataStoreEditor.putValue(Settings.KEY_ADDONS_VR_HAS_NAVIGATOR, false, true);
            dialog.dismiss();
            new AddonManagerDialog(a).show();
        });

        View indeterminateGotoView = dialog.findViewById(R.id.addonIndeterminateGotoButton);
        if (indeterminateGotoView != null) indeterminateGotoView.setOnClickListener(v -> {
            a.dataStoreEditor.putValue(Settings.KEY_ADDONS_VR_TYPE_KNOWN, false, true);
            dialog.dismiss();
            new AddonManagerDialog(a).show();
        });

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
        AlertDialog dialog = new BasicDialog<>(a, R.layout.dialog_info_redirect_activate).show();
        if (dialog != null) dialog.findViewById(R.id.openAccessibilitySettings).setOnClickListener(v -> {
            // Navigate to accessibility settings
            Intent localIntent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
            localIntent.setPackage("com.android.settings");
            assert a != null;
            a.startActivity(localIntent);
        });
    }

    protected static void showDockDialog() {
        Activity a = LauncherActivity.getForegroundInstance();
        new BasicDialog<>(a, R.layout.dialog_info_dock_add).show();
    }
}
