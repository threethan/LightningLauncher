package com.threethan.launchercore.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class LcContainerView extends ViewGroup {
    private final Object lock = new Object();

    public LcContainerView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public LcContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public LcContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        synchronized (lock) {
            // Remove existing child if present
            if (getChildCount() > 0) removeAllViews();
            super.addView(child, index, params);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        synchronized (lock) {
            if (getChildCount() == 0) {
                setMeasuredDimension(
                        resolveSize(70, widthMeasureSpec),
                        resolveSize(70, heightMeasureSpec)
                );
                return;
            }

            final View child = getChildAt(0);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(
                    child.getMeasuredWidth(),
                    child.getMeasuredHeight()
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        synchronized (lock) {
            if (getChildCount() > 0) {
                final View child = getChildAt(0);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        synchronized (lock) {
            return new LayoutParams(getContext(), attrs);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        synchronized (lock) {
            return new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            );
        }
    }
}