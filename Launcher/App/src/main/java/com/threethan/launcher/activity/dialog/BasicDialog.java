package com.threethan.launcher.activity.dialog;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.Platform;

import java.lang.ref.WeakReference;

/*
    Dialog

    This provides a wrapper for AlertDialog.Builder that makes it even easier to create an alert
    dialog from a layout resource
 */
public class BasicDialog<T extends Context> extends AbstractDialog<T> {
    private static WeakReference<Activity> activityContextWeakReference;

    @Nullable
    public static Activity getActivityContext() {
        return activityContextWeakReference.get();
    }
    public static void setActivityContext(Activity activityContext) {
        activityContextWeakReference = new WeakReference<>(activityContext);
    }


    final int resource;

    /**
     * Constructs a new dialog from a context and resource.
     * Use .show() to show and return an AlertDialog.
     * @param context Context to show the dialog
     * @param resource Resource of the dialog's layout
     */
    public BasicDialog(T context, int resource) {
        super(context);
        this.resource = resource;
    }
    @Nullable
    public AlertDialog show() {
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.dialog).setView(resource).create();

        if (dialog.getWindow() == null) return null;
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog);
        dialog.getWindow().setDimAmount(0.3f);
        final View rootView = dialog.getWindow().getDecorView().findViewById(android.R.id.content).getRootView();
        rootView.setLayerType(View.LAYER_TYPE_HARDWARE, new Paint());

        ObjectAnimator animator = ObjectAnimator.ofFloat(rootView, "TranslationY", 100, 0);
        animator.setDuration(300);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.start();

        dialog.show();
        return dialog;
    }

    public static void toast(String string) {
        toast(string, "", false);
    }

    public static void toast(String stringMain, String stringBold, boolean isLong) {
        if (getActivityContext() == null) return;

        // Real toast doesn't block dpad input
        if (!Platform.isVr(getActivityContext())) {
            Toast.makeText(getActivityContext() , stringMain + " " + stringBold,
                    (isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)).show();
            return;
        }

        // Fake toast for the Quest
        AlertDialog dialog = new AlertDialog.Builder(getActivityContext(), R.style.dialogToast).setView(R.layout.dialog_toast).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_TOAST);
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog_transparent);
            dialog.getWindow().setDimAmount(0.0f);
        }
        dialog.show();

        ((TextView) dialog.findViewById(R.id.toastTextMain)).setText(stringMain);
        ((TextView) dialog.findViewById(R.id.toastTextBold)).setText(stringBold);

        // Dismiss if not done automatically
        dialog.findViewById(R.id.toastTextMain).postDelayed(dialog::dismiss,
                isLong ? 5000 : 1750);
    }
}
