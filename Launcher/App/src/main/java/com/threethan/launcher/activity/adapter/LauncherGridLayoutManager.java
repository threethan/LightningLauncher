package com.threethan.launcher.activity.adapter;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.BuildConfig;
import com.threethan.launchercore.util.LcDialog;

import java.util.Arrays;

/**
 * Extends GridLayoutManager to prefetch more aggressively,
 * and to scroll to a target position even as data changes
 */
public class LauncherGridLayoutManager extends GridLayoutManager {
    /**
     * @noinspection unused
     */
    public LauncherGridLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setMeasurementCacheEnabled(true);
        setItemPrefetchEnabled(true);
        setInitialPrefetchItemCount(100);
    }

    public LauncherGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
        setMeasurementCacheEnabled(true);
        setItemPrefetchEnabled(true);
        setInitialPrefetchItemCount(100);
    }

    static {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable instanceof IllegalArgumentException
                    && Arrays.stream(throwable.getStackTrace()).anyMatch(el -> el.getClassName().toLowerCase().contains("recyclerview"))) {
                // This exception rarely occurs due to async ops in ViewHolders
                if (BuildConfig.DEBUG) {
                    LcDialog.toast("RecyclerView recycling error ignored");
                    Log.w("RecyclerViewFix", "Ignoring known IllegalArgumentException", throwable);
                }
            } else {
                Thread.UncaughtExceptionHandler dh = Thread.getDefaultUncaughtExceptionHandler();
                if (dh != null) dh.uncaughtException(thread, throwable);
            }
        });
    }

    @Override
    public void collectAdjacentPrefetchPositions(int dx, int dy, RecyclerView.State state,
                                                 LayoutPrefetchRegistry layoutPrefetchRegistry) {
        // Prefetch all items (hopefully)
        for (int i = 0; i < Math.min(getItemCount(), 100); i++) {
            layoutPrefetchRegistry.addPosition(i, i);
        }
    }

    @Override
    public void collectInitialPrefetchPositions(int adapterItemCount, LayoutPrefetchRegistry layoutPrefetchRegistry) {
        // Prefetch all items (hopefully)
        for (int i = 0; i < Math.min(adapterItemCount, 100); i++) {
            layoutPrefetchRegistry.addPosition(i, i);
        }
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            return super.scrollVerticallyBy(dy, recycler, state);
        } catch (IllegalArgumentException e) {
            Log.e("RecyclerViewFix", "Ignoring known IllegalArgumentException", e);
            if (BuildConfig.DEBUG) LcDialog.toast("RecyclerView scroll error ignored");
            return 0;
        }
    }
}