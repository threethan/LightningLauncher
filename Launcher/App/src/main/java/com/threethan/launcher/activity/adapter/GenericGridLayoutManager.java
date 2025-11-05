package com.threethan.launcher.activity.adapter;

import androidx.recyclerview.widget.GridLayoutManager;

public interface GenericGridLayoutManager {
    void setOrientation(int horizontal);

    int getOrientation();

    void setSpanSizeLookup(GridLayoutManager.SpanSizeLookup spanSizeLookup);

    void setSpanCount(int nCol);

    int getSpanCount();
}
