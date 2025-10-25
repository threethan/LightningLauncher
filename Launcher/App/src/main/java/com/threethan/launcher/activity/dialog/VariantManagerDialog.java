package com.threethan.launcher.activity.dialog;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.threethan.launcher.BuildConfig;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.AppExt;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launchercore.util.LcDialog;

/**
 * Dialog for managing different app variants.
 */
public class VariantManagerDialog extends LcDialog<LauncherActivity> {
    /**
     * Constructs a new VariantManagerDialog.
     * @param context The LauncherActivity context.
     */
    public VariantManagerDialog(LauncherActivity context) {
        super(context, R.layout.dialog_variants);
    }

    /**
     * Shows the dialog and sets up its UI elements.
     * @return The displayed AlertDialog instance, null if failed.
     */
    @Nullable
    @Override
    public AlertDialog show() {
        AlertDialog d = super.show();
        if (d == null) return null;

        d.findViewById(R.id.exitButton).setOnClickListener(
                v -> d.dismiss()
        );

        setupVariantSection(d.findViewById(R.id.variantMetastore), VariantHelper.VARIANT_METASTORE);
        setupVariantSection(d.findViewById(R.id.variantPlaystore), VariantHelper.VARIANT_PLAYSTORE);
        setupVariantSection(d.findViewById(R.id.variantSideload), VariantHelper.VARIANT_SIDELOAD);

        return d;
    }

    /**
     * Sets up a variant section in the dialog.
     * @param view        The root view of the variant section.
     * @param packageName The package name of the variant to check.
     */
    public void setupVariantSection(ViewGroup view, String packageName) {
        boolean isInstalled = AppExt.packageExists(packageName);
        if (isInstalled) {
            view.setVisibility(View.VISIBLE);

            ViewGroup buttons = view.findViewById(R.id.variantButtons);

            if (BuildConfig.APPLICATION_ID.equals(packageName)) {
                buttons.setVisibility(View.GONE);
                view.findViewById(R.id.variantCurrent).setVisibility(View.VISIBLE);
            }

            buttons.findViewById(R.id.variantInfo).setOnClickListener(v -> AppExt.openInfo(a, packageName));
            buttons.findViewById(R.id.variantLaunch).setOnClickListener(v -> LaunchExt.launchApp(a, AppExt.infoFor(packageName)));

            buttons.findViewById(R.id.variantTransfer).setOnClickListener(v ->
                    SyncCoordinator.transferSettingsTo(a, packageName)
            );
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
