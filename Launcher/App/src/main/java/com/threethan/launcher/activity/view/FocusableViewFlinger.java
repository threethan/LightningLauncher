package com.threethan.launcher.activity.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.threethan.launcher.R;

public class FocusableViewFlinger extends ViewFlinger {
    /**
     * Used to inflate the com.google.android.ext.workspace.Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the com.google.android.ext.workspace.Workspace's
     *                customization values.
     */
    public FocusableViewFlinger(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean isFocused() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).isFocused()) return true;
        }
        return false;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);
        if (isFocused()) {
            Drawable drawable = getContext().getDrawable(R.drawable.lc_fg_focused);
            if (drawable != null) {
                drawable.setBounds(0, 0, getWidth(), getHeight());
                drawable.draw(canvas);
            }
        }
    }
}
