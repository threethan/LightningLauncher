package com.threethan.launchercore.util;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.AbstractDialog;
import com.threethan.launchercore.Core;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
    LcDialog

    This provides a wrapper for AlertDialog.Builder that makes it even easier to create an alert
    dialog from a layout resource
 */
public class LcDialog<T extends Context> extends AbstractDialog<T> {
    final int resource;
    private static final Set<WeakReference<AlertDialog>> dialogInstances = new HashSet<>();

    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     * @param context Context to show the dialog
     * @param resource Resource of the dialog's layout
     */
    public LcDialog(T context, int resource) {
        super(context);
        this.resource = resource;
    }

    /**
     * Closes all open dialogs created by BasicDialog,
     * for when activity is finishing.
     */
    public static void closeAll() {
        for (WeakReference<AlertDialog> ref : dialogInstances) {
            if (ref != null && ref.get() != null) {
                try {
                    ref.get().dismiss();
                } catch (Exception ignored) {}
            }
        }
        dialogInstances.clear();
        try {
            currentToast.dismiss();
            currentToast = null;
        } catch (Exception ignored) {}
    }

    @Nullable
    public AlertDialog show() {
        AlertDialog dialog = new CustomDialog.Builder(a).setView(resource).create();

        if (dialog.getWindow() == null) return null;
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog);
        final View rootView = dialog.getWindow().getDecorView().findViewById(android.R.id.content).getRootView();
        rootView.setLayerType(View.LAYER_TYPE_HARDWARE, new Paint());

        if (!LauncherActivity.shouldReduceMotion()) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(rootView, "TranslationY", 100, 0);
            animator.setDuration(300);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.start();
        }

        dialog.show();
        LcDialog.dialogInstances.add(new WeakReference<>(dialog));

        View dismissButton = dialog.findViewById(R.id.dismissButton);
        if (dismissButton != null) dismissButton.setOnClickListener(view -> dialog.dismiss());

        return dialog;
    }

    public static void toast(CharSequence string) {
        toast(string, "", false);
    }

    // Custom dialog that catches exceptions on dismiss
    protected static class ToastDialog extends AlertDialog {
        protected ToastDialog(Context context) {
            super(context);
        }

        @Override
        public void dismiss() {
            try {
                super.dismiss();
            } catch (Exception ignored) {}
        }
    }
    private static AlertDialog currentToast = null;
    public static void toast(CharSequence stringMain, CharSequence stringBold, boolean isLong) {
        if (Core.context() == null) return;
        Log.d("Toast", stringMain + " " + stringBold);

        // Real toast doesn't block dpad input
        if (!Platform.isVr()) {
            Toast.makeText(Core.context() , stringMain + " " + stringBold,
                    (isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show();
            return;
        }

        try {
            LayoutInflater inflater =
                    Objects.requireNonNull(LauncherActivity.getForegroundInstance())
                            .getLayoutInflater();
            @SuppressLint("InflateParams")
            View layout = inflater.inflate(R.layout.dialog_toast, null);

            TextView textMain = layout.findViewById(R.id.toastTextMain);
            TextView textBold = layout.findViewById(R.id.toastTextBold);
            textMain.setText(stringMain);
            textBold.setText(stringBold);

            AlertDialog dialog = new ToastDialog(LauncherActivity.getForegroundInstance());
            dialog.setView(layout);
            dialog.setCancelable(true);

            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
            window.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    0xFFFFFFFF
            );
            textMain.postDelayed(dialog::dismiss, isLong ? 3500 : 2000);
            if (currentToast != null) {
                try {currentToast.dismiss();} catch (Exception ignored) {}
            }
            currentToast = dialog;
            dialog.show();
        } catch (Exception e) {
            Log.w("Toast", "Failed to show toast", e);
        }
    }

    public static void initSpinner(Spinner spinner, int array_res,
                                   Consumer<Integer> onPositionSelected, int initialSelection) {
        Context foregroundContext = LauncherActivity.getForegroundInstance();
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                foregroundContext != null ? foregroundContext : Core.context(),
                array_res, android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onPositionSelected.accept(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (initialSelection < 0 || initialSelection >= adapter.getCount())
            Log.e("SettingsArray", "Invalid position "+initialSelection);
        else spinner.setSelection(initialSelection);
    }
}
