package com.threethan.launcher.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.threethan.launcher.MainActivity;
import com.threethan.launcher.R;
import com.threethan.launcher.SettingsProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupsAdapter extends BaseAdapter {
    public static final int MAX_GROUPS = 12;
    public static final String HIDDEN_GROUP = "HIDDEN!";
    private final MainActivity mainActivity;
    private final List<String> appGroups;
    private final Set<String> selectedGroups;
    private final SettingsProvider settingsProvider;
    private final boolean isEditMode;

    /**
     * Create new adapter
     */
    public GroupsAdapter(MainActivity activity, boolean editMode) {
        mainActivity = activity;
        isEditMode = editMode;
        settingsProvider = SettingsProvider.getInstance(activity);

        SettingsProvider settings = SettingsProvider.getInstance(mainActivity);
        appGroups = settings.getAppGroupsSorted(false);
        if (editMode) {
            appGroups.add(HIDDEN_GROUP);
            appGroups.add("+ " + mainActivity.getString(R.string.add_group));
        }
        selectedGroups = settings.getSelectedGroups();
    }

    public int getCount() {
        return appGroups.size();
    }

    public String getItem(int position) {
        return appGroups.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        final TextView textView;
        final View menu;

        ViewHolder(View itemView) {
            textView = itemView.findViewById(R.id.textLabel);
            menu = itemView.findViewById(R.id.menu);
        }
    }

    private void setTextViewValue(TextView textView, String value) {
        if (HIDDEN_GROUP.equals(value)) {
            textView.setText(" -  " + mainActivity.getString(R.string.apps_hidden));
        } else {
            textView.setText(value);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            convertView = inflater.inflate(R.layout.lv_group, parent, false);

            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        setTextViewValue(holder.textView, getItem(position));

        // set menu action
        Drawable drawable = holder.menu.getContext().getDrawable(R.drawable.ic_info);
        assert drawable != null;
        drawable.setColorFilter(Color.parseColor("#90000000"), PorterDuff.Mode.SRC_ATOP);
        holder.menu.setBackground(drawable);
        holder.menu.setOnClickListener(view -> {

            final Map<String, String> apps = settingsProvider.getAppList();
            final Set<String> appGroupsList = settingsProvider.getAppGroups();
            final String oldGroupName = settingsProvider.getAppGroupsSorted(false).get(position);

            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            builder.setView(R.layout.dialog_group_details);

            AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog);
            dialog.show();

            final EditText groupNameInput = dialog.findViewById(R.id.group_name);
            groupNameInput.setText(oldGroupName);

            dialog.findViewById(R.id.ok).setOnClickListener(view1 -> {
                String newGroupName = groupNameInput.getText().toString();
                if (newGroupName.length() > 0) {
                    appGroupsList.remove(oldGroupName);
                    appGroupsList.add(newGroupName);
                    Map<String, String> updatedAppList = new HashMap<>();
                    for (String packageName : apps.keySet()) {
                        if (apps.get(packageName).compareTo(oldGroupName) == 0) {
                            updatedAppList.put(packageName, newGroupName);
                        } else {
                            updatedAppList.put(packageName, apps.get(packageName));
                        }
                    }
                    HashSet<String> selectedGroup = new HashSet<>();
                    selectedGroup.add(newGroupName);
                    settingsProvider.setSelectedGroups(selectedGroup);
                    settingsProvider.setAppGroups(appGroupsList);
                    settingsProvider.setAppList(updatedAppList);
                    mainActivity.reloadUI();
                }
                dialog.dismiss();
            });

            dialog.findViewById(R.id.group_delete).setOnClickListener(view12 -> {
                HashMap<String, String> newAppList = new HashMap<>();
                for (String packageName : apps.keySet()) {
                    if (oldGroupName.equals(apps.get(packageName))) {
                        newAppList.put(packageName, HIDDEN_GROUP);
                    } else {
                        newAppList.put(packageName, apps.get(packageName));
                    }
                }
                settingsProvider.setAppList(newAppList);

                appGroupsList.remove(oldGroupName);
                settingsProvider.setAppGroups(appGroupsList);

                Set<String> firstSelectedGroup = new HashSet<>();
                try{
                    firstSelectedGroup.add(settingsProvider.getAppGroupsSorted(false).get(0));
                    settingsProvider.setSelectedGroups(firstSelectedGroup);
                }catch(IndexOutOfBoundsException e){
                    settingsProvider.resetGroups();
                }
                mainActivity.reloadUI();
                dialog.dismiss();
            });
        });

        // set the look
        setLook(position, convertView, holder.menu);

        // set drag and drop
        final View finalConvertView = convertView;
        convertView.setOnDragListener((view, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                int[] colors = new int[]{Color.argb(192, 128, 128, 255), Color.TRANSPARENT};
                GradientDrawable.Orientation orientation = GradientDrawable.Orientation.LEFT_RIGHT;
                finalConvertView.setBackground(new GradientDrawable(orientation, colors));
            } else if (event.getAction() == DragEvent.ACTION_DRAG_EXITED) {
                setLook(position, finalConvertView, holder.menu);
            } else if (event.getAction() == DragEvent.ACTION_DROP) {
                // add group or hidden group selection
                String name = appGroups.get(position);
                List<String> appGroupsList = settingsProvider.getAppGroupsSorted(false);
                if (appGroupsList.size() + 1 == position) {
                    name = settingsProvider.addGroup();
                } else if (appGroupsList.size() == position) {
                    name = HIDDEN_GROUP;
                }

                // move app into group
                String packageName = mainActivity.getSelectedPackage();
                Set<String> selectedGroup = settingsProvider.getSelectedGroups();
                Map<String, String> apps = settingsProvider.getAppList();
                apps.remove(packageName);
                apps.put(packageName, name);
                settingsProvider.setAppList(apps);

                // false to dragged icon fly back
                return !selectedGroup.contains(name);
            }
            return true;
        });

        TextView textView = convertView.findViewById(R.id.textLabel);
        setTextViewValue(textView, appGroups.get(position));

        return convertView;
    }

    public void setGroup(String packageName, String groupName) {
        Map<String, String> apps = settingsProvider.getAppList();
        apps.remove(packageName);
        apps.put(packageName, groupName);
        settingsProvider.setAppList(apps);
    }

    private void setLook(int position, View itemView, View menu) {
        boolean isSelected = selectedGroups.contains(appGroups.get(position));

        if (isSelected) {
            boolean isLeft = (position == 0) || !selectedGroups.contains(appGroups.get(position - 1));
            boolean isRight = (position + 1 >= appGroups.size()) || !selectedGroups.contains(appGroups.get(position + 1));

            int shapeResourceId;
            if (isLeft && isRight) {
                shapeResourceId = R.drawable.selected_tab;
            } else if (isLeft) {
                shapeResourceId = R.drawable.left_selected_tab;
            } else if (isRight) {
                shapeResourceId = R.drawable.right_selected_tab;
            } else {
                shapeResourceId = R.drawable.middle_selected_tab;
            }
            itemView.setBackgroundResource(shapeResourceId);
            TextView textView = itemView.findViewById(R.id.textLabel);
            textView.setTextColor(Color.parseColor("#FF000000")); // set selected tab text color
            if (isEditMode && (position < getCount() - 2)) {
                menu.setVisibility(View.VISIBLE);
            } else {
                menu.setVisibility(View.GONE);
            }
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT);
            TextView textView = itemView.findViewById(R.id.textLabel);
            textView.setTextColor(Color.parseColor("#90000000")); // set unselected tab text color
            menu.setVisibility(View.GONE);
        }
    }
}