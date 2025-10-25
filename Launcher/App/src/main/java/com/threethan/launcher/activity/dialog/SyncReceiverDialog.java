package com.threethan.launcher.activity.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.VariantHelper;
import com.threethan.launchercore.util.LcDialog;

public class SyncReceiverDialog extends LcDialog<Context> {
    private final String fromPackage;
    private final Context context;
    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     *
     * @param context  Context to show the dialog
     */
    public SyncReceiverDialog(Context context, String fromPackage) {
        super(context, R.layout.dialog_sync_receive);
        this.context = context;
        this.fromPackage = fromPackage;
    }

    @Nullable
    @Override
    public AlertDialog show() {
        AlertDialog d = super.show();
        if (d == null) return null;

        String toPackage = context.getPackageName();

        TextView syncFromName = d.findViewById(R.id.syncFromName);
        TextView syncToName = d.findViewById(R.id.syncToName);

        TextView syncFromDetails = d.findViewById(R.id.syncFromDetails);
        TextView syncToDetails = d.findViewById(R.id.syncToDetails);

        ImageView syncFromIcon = d.findViewById(R.id.syncFromIcon);
        ImageView syncToIcon = d.findViewById(R.id.syncToIcon);

        syncFromName.setText(VariantHelper.getNameResId(fromPackage));
        syncToName.setText(VariantHelper.getNameResId(toPackage));

        syncFromDetails.setText(VariantHelper.getDetailsResId(fromPackage));
        syncToDetails.setText(VariantHelper.getDetailsResId(toPackage));

        syncFromIcon.setImageResource(VariantHelper.getIconResId(fromPackage));
        syncToIcon.setImageResource(VariantHelper.getIconResId(toPackage));

        View cancelButton = d.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(view -> d.dismiss());

        View confirmButton = d.findViewById(R.id.confirm);
        confirmButton.setOnClickListener(view -> {
            SyncCoordinator.syncFromPackage(context, fromPackage);
            d.dismiss();
        });
        return d;
    }
}
