package com.threethan.launcher.adapter;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.Settings;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.support.GroupDetailsDialog;
import com.threethan.launcher.support.SettingsManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.lv_group, parent, false);

        GroupViewHolder holder = new GroupViewHolder(view);
        setActions(holder);
        return holder;
    }

    private void setActions(GroupViewHolder holder) {
        holder.textView.setOnClickListener((view) -> launcherActivity.clickGroup(holder.position));
        holder.textView.setOnLongClickListener((view) -> {
            if (launcherActivity.longClickGroup(holder.position)) holder.menu.callOnClick();
            return true;
        });

        holder.textView.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER)
                holder.textView.setBackgroundResource(R.drawable.bkg_hover_button_bar_hovered);
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT)
                holder.textView.setBackground(null);
            return false;
        });
        holder.textView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus || view.isHovered()) holder.textView.setBackgroundResource(R.drawable.bkg_hover_button_bar_hovered);
            else holder.textView.setBackground(null);
        });
        holder.menu.setOnClickListener(view ->
                new GroupDetailsDialog(launcherActivity, holder.position).show());
    }
    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, @SuppressLint("RecyclerView") int position) {
        setLook(position, holder.view, holder.menu);
        TextView textView = holder.view.findViewById(R.id.textLabel);
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
            this.textView = view.findViewById(R.id.textLabel);
            this.menu = view.findViewById(R.id.menu);
        }
    }

    private void setTextViewValue(TextView textView, String value) {
        if (Settings.HIDDEN_GROUP.equals(value)) textView.setText(launcherActivity.getString(R.string.apps_hidden));
        else textView.setText(value);
    }

    public void setGroup(String packageName, String groupName) {
        Map<String, String> appGroupMap = SettingsManager.getAppGroupMap();
        appGroupMap.remove(packageName);
        appGroupMap.put(packageName, groupName);
        SettingsManager.setAppGroupMap(appGroupMap);
    }

    private void setLook(int position, View itemView, View menu) {
        boolean isSelected = selectedGroups.contains(appGroups.get(position));

        if (isSelected) {
            boolean isLeft = (position == 0) || !selectedGroups.contains(appGroups.get(position - 1));
            boolean isRight = (position + 1 >= appGroups.size()) || !selectedGroups.contains(appGroups.get(position + 1));

            int shapeResourceId;
            if (isLeft && isRight) {
                shapeResourceId = R.drawable.tab_selected;
            } else if (isLeft) {
                shapeResourceId = R.drawable.tab_selected_left;
            } else if (isRight) {
                shapeResourceId = R.drawable.tab_selected_right;
            } else {
                shapeResourceId = R.drawable.tab_selected_middle;
            }
            itemView.setBackgroundResource(shapeResourceId);
            itemView.setBackgroundTintList(ColorStateList.valueOf(
                    Color.parseColor(LauncherActivity.darkMode ? "#50000000" : "#FFFFFF")));
            TextView textView = itemView.findViewById(R.id.textLabel);
            textView.setTextColor(Color.parseColor(
                    LauncherActivity.darkMode ? "#FFFFFFFF" : "#FF000000")); // set selected tab text color

            if (isEditMode && (position < getCount() - 2)) menu.setVisibility(View.VISIBLE);
            else                                           menu.setVisibility(View.GONE);
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT);
            TextView textView = itemView.findViewById(R.id.textLabel);
            textView.setTextColor(Color.parseColor(
                    LauncherActivity.darkMode ? "#98FFFFFF" : "#98000000")); // set unselected tab text color
            menu.setVisibility(View.GONE);
        }
    }
}