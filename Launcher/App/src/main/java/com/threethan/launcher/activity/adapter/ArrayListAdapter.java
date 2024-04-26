package com.threethan.launcher.activity.adapter;

import android.annotation.SuppressLint;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An extension of {@link RecyclerView} which provides functions for using and manipulating
 * and underlying list of item objects. Useful to enabling animations on changing data.
 *
 * @param <T> The type of object used as an item in the list, each of which may be given a view
 * @param <H> The type of {@link RecyclerView.Adapter} to use for the underlying RecyclerView
 * @noinspection unused
 */
public abstract class ArrayListAdapter<T, H extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<H> {
    /**
     * The underlying list of items. It may be manupulated directly by child classes,
     * but it is preferable to use functions such as addItem, removeItem, setItems
     */
    protected List<T> items = Collections.synchronizedList(new ArrayList<>());

    /**
     * Sets the content of the item list, while notifying additions and removals for animations.
     * <p>
     * Items are kept unchanged and in place if possible, so it is much faster than using
     * notifyDataSetChanged()
     * @param newItems the new list of items
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<T> newItems) {
        final Map<Integer, Integer> movedPrevByNew = new HashMap<>(); // to -> from
        for (T item : new ArrayList<>(items)) {
            if (!newItems.contains(item)) {
                int i = items.indexOf(item);
                items.remove(i);
                notifyItemRemoved(i);
            }
        }
        for (T item : newItems) {
            if (!items.contains(item)) {
                int i = newItems.indexOf(item);
                items.add(i, item);
                notifyItemInserted(i);
            }
        }
        for (T item : newItems) {
            final int oldIndex = items.indexOf(item);
            final int newIndex = newItems.indexOf(item);
            if (oldIndex != newIndex) {
                items.remove(oldIndex);
                items.add(newIndex, item);
                notifyItemMoved(oldIndex, newIndex);
            }
        }
        // Failsafe, just in case
        if (!items.equals(newItems)) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }
    }

    /**
     * Returns the total number of items in the data list.
     * @return The total number of items in this adapter.
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Calls notifyItemChanged using an item instead of an index
     * @param item that changed
     */
    public void notifyItemChanged(T item) {
        notifyItemChanged(items.indexOf(item));
    }

    /**
     * Remove an item and notify its removal for animation
     * @param item that will be removed
     */
    public void removeItem(T item) {
        int index = items.indexOf(item);
        items.remove(item);
        notifyItemRemoved(index);
    }
    /**
     * Add an item to the end of the list and notify its addition for animation
     * @param item that will be added
     */
    public void addItem(T item) {
        items.add(item);
        notifyItemInserted(items.indexOf(item));
    }

    /**
     * Calls notifyItemRangeChanged on the whole list
     */
    public void notifyAllChanged() {
        notifyItemRangeChanged(0, getItemCount());
    }
}
