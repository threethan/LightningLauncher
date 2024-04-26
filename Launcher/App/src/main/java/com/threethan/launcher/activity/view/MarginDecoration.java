package com.threethan.launcher.activity.view;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An {@link RecyclerView.ItemDecoration} that allows for items to be easily
 * given uniform margins
 */
public class MarginDecoration extends RecyclerView.ItemDecoration {
    private int margin;

    /**
     * Creates the MarginDecoration item decoration which can be added to a {@link RecyclerView}
     * @param margin The margin to be added on all sides
     */
    public MarginDecoration(int margin) {
        this.margin = margin;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.left = margin;
        outRect.right = margin;
        outRect.bottom = margin;
        outRect.top = margin;
    }

    /** Updates the margin of the decorator */
    public void setMargin(int margin) {
        this.margin = margin;
    }
}