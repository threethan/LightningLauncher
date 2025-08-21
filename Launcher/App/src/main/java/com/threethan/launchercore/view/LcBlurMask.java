package com.threethan.launchercore.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * Masks an area with a blur effect.
 */
public class LcBlurMask extends View {
    public LcBlurMask(Context context) {
        super(context);
    }

    public LcBlurMask(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LcBlurMask(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** @noinspection unused*/
    public LcBlurMask(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LcBlurCanvas.useRenderRect = true;
        getViewTreeObserver().addOnPreDrawListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LcBlurCanvas.useRenderRect = false;
        getViewTreeObserver().removeOnPreDrawListener(listener);
    }

    private final ViewTreeObserver.OnPreDrawListener listener = () -> getGlobalVisibleRect(LcBlurCanvas.renderRect);
}
