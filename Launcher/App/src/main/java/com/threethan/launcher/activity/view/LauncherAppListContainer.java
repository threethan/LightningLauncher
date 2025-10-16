package com.threethan.launcher.activity.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;

import com.threethan.launchercore.view.LcBlurCanvas;

public class LauncherAppListContainer extends LcBlurCanvas {
    public LauncherAppListContainer(Context context) {
        super(context);
    }

    public LauncherAppListContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LauncherAppListContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private boolean allowLayout = false;
    private boolean requestingLayout = false;

    public void setAllowLayout(boolean allowLayout) {
        this.allowLayout = allowLayout;
        if (requestingLayout) {
            requestingLayout = false;
            requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (allowLayout) super.onLayout(changed, l, t, r, b);
        else requestingLayout = true;
    }
}
