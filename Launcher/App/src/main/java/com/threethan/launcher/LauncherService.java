package com.threethan.launcher;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.chainload.ChainLoadActivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
    This class runs as a service, but does not do anything on its own.

    It is used by LauncherActivity to store the view containing the main interface in memory,
    meaning if the activity is finished and reopened, it may re-use the same preloaded view.

    It also provides a number of helper functions that enable this & help with multitasking
 */

public class LauncherService extends Service {
    private final IBinder binder = new LocalBinder();
    private final static ConcurrentHashMap<Integer, View> viewByIndex = new ConcurrentHashMap<>();
    public class LocalBinder extends Binder {
        public LauncherService getService() {
            return LauncherService.this;
        }
    }
    private static final Map<LauncherActivity, Integer> activityByIndex = Collections.synchronizedMap(new HashMap<>());
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Creates an returns a new view which can be used as the main view for the launcher
     * @param activity The activity calling this
     * @param root The viewgroup the view will be added to
     * @return The view itself
     */
    public View getNewView(LauncherActivity activity, ViewGroup root) {
        final int index = getNewActivityIndex();

        View view = View.inflate(activity, R.layout.activity_main, root);
        viewByIndex.put(index, view);
        activityByIndex.put(activity, index);

        return view;
    }

    /**
     * Gets an existing inactive launcher view, which prevents the need to create a new one
     * (Operates on a similar concept to RecyclerViews)
     * @param activity The activity calling this
     * @param root The viewgroup the view will be added to
     * @return The view itself
     */
    public View getExistingView(LauncherActivity activity, ViewGroup root) {
        final int index = getNewActivityIndex();
        View view = viewByIndex.get(index);

        assert view != null;
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        activityByIndex.put(activity, index);

        root.addView(view);
        return view;
    }

    /**
     * Checks if we can get an already existing inactive launcher view instead of making a new one
     * @return true if we will be given an existing view
     */
    public boolean checkForExistingView() {
        return viewByIndex.containsKey(getNewActivityIndex());
    }
    protected int getNewActivityIndex() {
        int i = 0;
        while(activityByIndex.containsValue(i)) i++;
        return i;
    }

    /**
     * Called on destroy to remove the launcher activity from our list
     */
    public void destroyed(LauncherActivity activity) {
        activityByIndex.remove(activity);
    }

    /**
     * Clears all activities so that Quest VR & Panel apps can run
     * Includes chainLoadActivities as well and launcherActivities
     */
    public void finishAllActivities() {
        for (Activity activity : activityByIndex.keySet()) activity.finishAndRemoveTask();
        for (Activity activity : ChainLoadActivity.activityList) activity.finishAndRemoveTask();
    }

    /**
     * Calls the consumer for each non-null launcher activity
     */
    public void forEachActivity(Consumer<LauncherActivity> consumer) {
        for (LauncherActivity activity: activityByIndex.keySet())
            if(activity != null) consumer.accept(activity);
    }
}
