package com.threethan.launcher.activity.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.threethan.launchercore.view.LcContainerView;

public class FocusDirectionAwareContainer extends LcContainerView {
    public FocusDirectionAwareContainer(@NonNull Context context) {
        super(context);
    }

    public FocusDirectionAwareContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusDirectionAwareContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private int direction = -1;
    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        this.direction = direction;
    }

    public int getFocusDirection() { return direction; }
}
