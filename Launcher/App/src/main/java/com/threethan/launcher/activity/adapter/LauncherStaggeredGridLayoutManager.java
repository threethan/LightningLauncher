package com.threethan.launcher.activity.adapter;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.activity.LauncherActivity;

import java.util.ArrayList;
import java.util.List;

// You can keep the other methods as they might be used by the adapter/other components

public class LauncherStaggeredGridLayoutManager extends RecyclerView.LayoutManager implements GenericGridLayoutManager {

    private int orientation = LinearLayout.VERTICAL;
    private int nSpans;

    public LauncherStaggeredGridLayoutManager(int spans) {
        super();
        this.nSpans = spans;
    }
    public LauncherStaggeredGridLayoutManager(int spans, int orientation) {
        this(spans);
        this.orientation = orientation;
    }

    @Override
    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    @Override
    public int getOrientation() {
        return orientation;
    }

    protected GridLayoutManager.SpanSizeLookup spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {;
        @Override
        public int getSpanSize(int position) {
            return 1;
        }
    };
    @Override
    public void setSpanSizeLookup(GridLayoutManager.SpanSizeLookup spanSizeLookup) {
        this.spanSizeLookup = spanSizeLookup;
    }

    @Override
    public void setSpanCount(int nSpans) {
        this.nSpans = nSpans;
        requestLayout();
    }

    @Override
    public int getSpanCount() {
        return nSpans;
    }

    int maxSpanInnerLength = 0;
    SparseArray<List<View>> viewsBySpan = new SparseArray<>();

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // Stop if there are no items or spans
        if (state.getItemCount() == 0 || nSpans == 0) {
            removeAndRecycleAllViews(recycler);
            return;
        }

        viewsBySpan.clear();

        // Detach and scrap all views for a fresh layout pass
        detachAndScrapAttachedViews(recycler);

        boolean vertical = (orientation == LinearLayout.VERTICAL);
        int parentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int parentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int parentLength = vertical ? parentWidth : parentHeight;
        int spanBreadth = parentLength / nSpans;
        spanBreadth -= LauncherActivity.iconMargin / 2;
        int spanMargin = LauncherActivity.iconMargin * 2 / 3;

        int spanOffset = vertical ? getPaddingLeft() : getPaddingTop();

        int[] spanInnerLengths = new int[nSpans];

        // Add staggers
        for (int s = 0; s < nSpans; s++) {
            if (s % 2 == 1) {
                spanInnerLengths[s] += (spanBreadth / 2);
            }
            if (vertical) {
                spanInnerLengths[s] += getPaddingTop();
            } else {
                spanInnerLengths[s] += getPaddingLeft();
            }
        }

        for (int i = 0; i < state.getItemCount(); i++) {
            View itemView = recycler.getViewForPosition(i);
            addView(itemView);

            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            int thisSpanSize = spanSizeLookup.getSpanSize(i);
            if (vertical) {
                lp.width = spanBreadth;
                lp.height = thisSpanSize * spanBreadth;
            } else {
                lp.height = spanBreadth;
                lp.width = thisSpanSize * spanBreadth;
            }
            itemView.setLayoutParams(lp);
            measureChild(itemView, 0, 0);

            int itemWidth = itemView.getMeasuredWidth();
            int itemHeight = itemView.getMeasuredHeight();

            // Find the shortest column to place the next item in
            int targetSpan = 0;
            int minInnerLength = Integer.MAX_VALUE;
            for (int s = 0; s < nSpans; s++) {
                if (spanInnerLengths[s] < (vertical ? parentHeight : parentWidth)
                        - spanBreadth - spanMargin) {
                    // Prefer spans that are not yet full
                    //   This means the first page will be laid out from top to bottom,
                    //   and only after that will layout truly be left to right
                    targetSpan = s;
                    break;
                }
                if (spanInnerLengths[s] < minInnerLength) {
                    minInnerLength = spanInnerLengths[s];
                    targetSpan = s;
                }
            }
            if (viewsBySpan.get(targetSpan) == null) {
                viewsBySpan.put(targetSpan, new ArrayList<>());
            }
            viewsBySpan.get(targetSpan).add(itemView);

            int left = vertical ? targetSpan * spanBreadth + spanMargin * targetSpan : spanInnerLengths[targetSpan];
            int top = vertical ? spanInnerLengths[targetSpan] : targetSpan * spanBreadth + spanMargin * targetSpan;

            if (vertical) {
                left += spanOffset;
            } else {
                top += spanOffset;
            }

            int right = left + itemWidth;
            int bottom = top + itemHeight;


            itemView.layout(left, top, right, bottom);

            // Update the len of the column
            spanInnerLengths[targetSpan] += vertical ? itemHeight : itemWidth;
        }

        // Update max span inner length (for scroll)
        maxSpanInnerLength = 0;
        for (int len : spanInnerLengths) {
            if (len > maxSpanInnerLength) {
                maxSpanInnerLength = len;
            }
        }
        if (vertical) {
            maxSpanInnerLength += getPaddingBottom();
        } else {
            maxSpanInnerLength += getPaddingRight();
        }

        // Keep from being offscreen when fewer views are now present
        clampScroll();

        if (vertical) {
            offsetChildrenVertical(-scrollOffset);
        } else {
            offsetChildrenHorizontal(-scrollOffset);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int scrollOffset = 0;

    @Override
    public boolean canScrollVertically() {
        return orientation == LinearLayout.VERTICAL;
    }

    @Override
    public boolean canScrollHorizontally() {
        return orientation == LinearLayout.HORIZONTAL;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (orientation != LinearLayout.VERTICAL || getChildCount() == 0) {
            return 0;
        }
        return scrollBy(dy, recycler, state);
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (orientation != LinearLayout.HORIZONTAL || getChildCount() == 0) {
            return 0;
        }
        return scrollBy(dx, recycler, state);
    }

    private int scrollBy(int delta, RecyclerView.Recycler ignoredRecycler, RecyclerView.State ignoredState) {
        if (getChildCount() == 0 || delta == 0) {
            return 0;
        }

        boolean vertical = (orientation == LinearLayout.VERTICAL);
        int previousOffset = scrollOffset;
        scrollOffset += delta;
        clampScroll();
        int scrolled = scrollOffset - previousOffset;
        if (scrolled != 0) {
            if (vertical) {
                offsetChildrenVertical(-scrolled);
            } else {
                offsetChildrenHorizontal(-scrolled);
            }
        }

        return scrolled;
    }

    private void clampScroll() {
        boolean vertical = (orientation == LinearLayout.VERTICAL);
        int maxScroll = maxSpanInnerLength - (vertical ? getHeight() : getWidth());
        if (scrollOffset <= 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            if (scrollOffset < 0) scrollOffset = 0;
        }
    }

}
