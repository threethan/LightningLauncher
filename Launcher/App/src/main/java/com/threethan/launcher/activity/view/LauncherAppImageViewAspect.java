package com.threethan.launcher.activity.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class LauncherAppImageViewAspect extends LauncherAppImageView {
    public LauncherAppImageViewAspect(Context context) {
        super(context);
    }

    public LauncherAppImageViewAspect(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LauncherAppImageViewAspect(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("unused")
    public LauncherAppImageViewAspect(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private float aspectRatio = 1.0f;
    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (aspectRatio > 0) {
            int measuredWidth = getMeasuredWidth();
            int measuredHeight = getMeasuredHeight();

            if (measuredWidth > 0) {
                int desiredHeight = Math.round(measuredWidth * 1f / aspectRatio);
                if (desiredHeight != measuredHeight) {
                    setMeasuredDimension(measuredWidth, desiredHeight);
                }
            }
        }
    }
}
