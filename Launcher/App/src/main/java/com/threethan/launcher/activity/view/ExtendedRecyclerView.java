package com.threethan.launcher.activity.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView that supports padding offsets for edge effects,
 * and supports proper total scroll tracking for onScrollChangeListener.
 */
public class ExtendedRecyclerView extends RecyclerView {

    public ExtendedRecyclerView(@NonNull Context context) {
        super(context);
    }

    public ExtendedRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return true;
    }

    @Override
    protected int getTopPaddingOffset() {
        return -getPaddingTop();
    }

    @Override
    protected int getBottomPaddingOffset() {
        return getPaddingBottom();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);


        if (mScrollChangeListener != null) {
            final int deltaX = l - oldl;
            final int deltaY = t - oldt;
            final int totalScrollX = computeHorizontalScrollOffset();
            final int totalScrollY = computeVerticalScrollOffset();
            mScrollChangeListener.onScrollChange(this,
                    totalScrollX,
                    totalScrollY,
                    totalScrollX - deltaX,
                    totalScrollY - deltaY);
        }}
    OnScrollChangeListener mScrollChangeListener;
    @Override
    public void setOnScrollChangeListener(OnScrollChangeListener l) {
        mScrollChangeListener = l;
    }

    @Override
    public void scrollToPosition(int position) {
        super.scrollToPosition(position);
        onScrollChanged(0, 0, 0, 0);
    }
}
