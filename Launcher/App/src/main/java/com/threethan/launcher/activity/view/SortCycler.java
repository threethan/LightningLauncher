package com.threethan.launcher.activity.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.support.SortHandler;
import com.threethan.launcher.activity.view.status.StatusAdaptableImageView;
import com.threethan.launcher.helper.PlaytimeHelper;

import java.util.function.Consumer;

public class SortCycler extends StatusAdaptableImageView {
    private SortHandler.SortMode lastMode;

    public SortCycler(Context context) {
        super(context);
    }

    public SortCycler(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SortCycler(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private Consumer<SortHandler.SortMode> onCycledListener;
    public void setOnCycledListener(Consumer<SortHandler.SortMode> listener) {
        this.onCycledListener = listener;
    }

    public void cycleTo(SortHandler.SortMode sortMode) {
        if (sortMode == lastMode) return;
        boolean hasUsage = PlaytimeHelper.hasUsagePermission();
        switch (sortMode) {
            case SEARCH, STANDARD -> setImageDrawable(R.drawable.ic_sort_standard);
            case RECENTLY_INSTALLED -> setImageDrawable(hasUsage ? R.drawable.ic_sort_recently_installed : R.drawable.ic_sort_recently_added);
            case RECENTLY_USED -> setImageDrawable(hasUsage ? R.drawable.ic_sort_recently_used : R.drawable.ic_sort_recently_launched);
        }
        if (onCycledListener != null)
            onCycledListener.accept(sortMode);
        lastMode = sortMode;
    }

    public void cycleNext() {
        cycleToNext(lastMode);
    }

    private void cycleToNext(SortHandler.SortMode currentMode) {
        SortHandler.SortMode[] modes = SortHandler.SortMode.values();
        int currentIndex = currentMode.ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        if (modes[nextIndex] == SortHandler.SortMode.SEARCH)
            nextIndex = (nextIndex + 1) % modes.length; // Skip search
        cycleTo(modes[nextIndex]);
    }

    private void setImageDrawable(int resId) {
        setImageDrawable(getContext().getDrawable(resId));
    }
}
