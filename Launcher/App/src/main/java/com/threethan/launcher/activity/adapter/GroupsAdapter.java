package com.threethan.launcher.activity.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.R;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.GroupDetailsDialog;
import com.threethan.launcher.activity.support.SettingsManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The adapter for the groups grid view.
 * <p>
 * It handles the appearance of groups and the edit group dialog.
 * Notably, it does not handle group selection; that's done in LauncherActivity
 */
public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {
    private LauncherActivity launcherActivity;
    private final List<String> appGroups;
    private final Set<String> selectedGroups;
    private final boolean isEditMode;
    public GroupsAdapter(LauncherActivity activity, boolean editMode) {
        launcherActivity = activity;
        isEditMode = editMode;

        SettingsManager settings = SettingsManager.getInstance(launcherActivity);
        appGroups = Collections.synchronizedList(settings.getAppGroupsSorted(false));

        if (!editMode) appGroups.remove(Settings.HIDDEN_GROUP);
        if (editMode && appGroups.size() <= Settings.MAX_GROUPS) appGroups.add("+ " + launcherActivity.getString(R.string.add_group));

        selectedGroups = settings.getSelectedGroups();
        if (!editMode) selectedGroups.remove(Settings.HIDDEN_GROUP);
        if (selectedGroups.isEmpty()) selectedGroups.addAll(appGroups);
    }
    public void setLauncherActivity(LauncherActivity val) {
        launcherActivity = val;
    }

    public int getCount() {
        return appGroups.size();
    }

    /** @noinspection ClassEscapesDefinedScope*/
    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_group, parent, false);

        GroupViewHolder holder = new GroupViewHolder(view);
        setActions(holder);
        return holder;
    }

    private void setActions(GroupViewHolder holder) {
        holder.textView.setOnClickListener((view)
                -> launcherActivity.clickGroup(holder.position, view));
        holder.textView.setOnLongClickListener((view) -> {
            if (launcherActivity.longClickGroup(holder.position)) holder.menu.callOnClick();
            return true;
        });

        holder.textView.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER)
                holder.textView.setBackgroundResource(R.drawable.tab_hovered);
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT)
                holder.textView.setBackground(null);
            return false;
        });
        holder.textView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus || view.isHovered()) holder.textView.setBackgroundResource(R.drawable.tab_focused);
            else holder.textView.setBackground(null);
        });
        holder.menu.setOnClickListener(view ->
                new GroupDetailsDialog(launcherActivity, holder.position).show());
    }
    /** @noinspection ClassEscapesDefinedScope*/
    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder,
                                 @SuppressLint("RecyclerView") int position) {
        setLook(position, holder.view, holder.menu);
        TextView textView = holder.view.findViewById(R.id.itemLabel);
        setTextViewValue(textView, appGroups.get(position));

        if (Objects.equals(launcherActivity.lastSelectedGroup, position))
            holder.view.post(holder.view::requestFocus);

        holder.position = position;
    }

    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return appGroups.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final View menu;
        final View view;
        int position;

        GroupViewHolder(View view) {
            super(view);
            this.view = view;
            this.textView = view.findViewById(R.id.itemLabel);
            this.menu = view.findViewById(R.id.menu);
        }
    }

    private void setTextViewValue(TextView textView, String value) {
        if (Settings.HIDDEN_GROUP.equals(value)) textView.setText(launcherActivity.getString(R.string.apps_hidden));
        else textView.setText(value);
    }

    public void moveAppToGroup(String packageName, String to) {
        if (Settings.HIDDEN_GROUP.equals(to)) {
            moveToHiddenGroup(packageName);
            return;
        }
        ConcurrentHashMap<String, Set<String>> gam = SettingsManager.getGroupAppsMap();

        // Remove from old group(s)
        for (String group : gam.keySet()) {
            Objects.requireNonNull(gam.get(group)).remove(packageName);
        }

        // Add to new group
        gam.computeIfAbsent(to, k -> new HashSet<>());
        Objects.requireNonNull(SettingsManager.getGroupAppsMap().get(to)).add(packageName);

        SettingsManager.setGroupAppsMap(gam);
    }

    public void copyAppToGroup(String packageName, String to) {
        if (Settings.HIDDEN_GROUP.equals(to)) {
            moveToHiddenGroup(packageName);
            return;
        }
        ConcurrentHashMap<String, Set<String>> gam = SettingsManager.getGroupAppsMap();

        // Add to new group
        gam.computeIfAbsent(to, k -> new HashSet<>());
        Objects.requireNonNull(SettingsManager.getGroupAppsMap().get(to)).add(packageName);

        SettingsManager.setGroupAppsMap(gam);
    }

    private void moveToHiddenGroup(String packageName) {
        ConcurrentHashMap<String, Set<String>> gam = SettingsManager.getGroupAppsMap();

        // Remove from old group(s)
        for (String group : gam.keySet())
            Objects.requireNonNull(gam.get(group)).remove(packageName);

        Set<String> hg = SettingsManager.getGroupAppsMap().get(Settings.HIDDEN_GROUP);
        if (hg != null) hg.add(packageName);
    }

    private void setLook(int position, View itemView, View menu) {
        boolean isSelected = selectedGroups.contains(appGroups.get(position));

        if (isSelected) {
            int shapeResourceId = getShapeResourceId(position);
            itemView.setBackgroundResource(shapeResourceId);
            TextView textView = itemView.findViewById(R.id.itemLabel);
            textView.setTextColor(LauncherActivity.darkMode ? 0xFFFFFFFF : 0xFF000000); // set selected tab text color
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);

            if (isEditMode && (position < getCount() - 2)) menu.setVisibility(View.VISIBLE);
            else                                           menu.setVisibility(View.GONE);
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT);
            TextView textView = itemView.findViewById(R.id.itemLabel);
            textView.setTextColor(LauncherActivity.darkMode ? 0x99FFFFFF : 0x99000000); // set unselected tab text color
            menu.setVisibility(View.GONE);
            textView.setTypeface(Typeface.create(textView.getTypeface(), Typeface.NORMAL));
        }
    }

    private int getShapeResourceId(int position) {
        boolean isLeft = (position == 0) || !selectedGroups.contains(appGroups.get(position - 1));
        boolean isRight = (position + 1 >= appGroups.size()) || !selectedGroups.contains(appGroups.get(position + 1));

        int shapeResourceId;
        final boolean dm = LauncherActivity.darkMode;
        if (isLeft && isRight) {
            shapeResourceId = dm ? R.drawable.tab_selected_dm : R.drawable.tab_selected_lm;
        } else if (isLeft) {
            shapeResourceId = dm ? R.drawable.tab_selected_left_dm : R.drawable.tab_selected_left_lm;
        } else if (isRight) {
            shapeResourceId = dm ? R.drawable.tab_selected_right_dm : R.drawable.tab_selected_right_lm;
        } else {
            shapeResourceId = dm ? R.drawable.tab_selected_middle_dm : R.drawable.tab_selected_middle_lm;
        }
        return shapeResourceId;
    }
}