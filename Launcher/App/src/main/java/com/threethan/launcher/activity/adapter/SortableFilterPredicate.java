package com.threethan.launcher.activity.adapter;

import android.content.pm.ApplicationInfo;

import com.threethan.launcher.helper.AppExt;

import java.util.function.Predicate;

public class SortableFilterPredicate implements Predicate<ApplicationInfo> {
    private final String[] filterParts;
    public SortableFilterPredicate(String filter) {
        super();
        filterParts = filter.toLowerCase().strip().split(" ");
    }

    @Override
    public boolean test(ApplicationInfo app) {
        String label = AppExt.getLabel(app).toLowerCase() + " " + app.packageName.toLowerCase();
        for (String filter : filterParts)
            if (!label.contains(filter)) return false;
        return true;
    }
}
