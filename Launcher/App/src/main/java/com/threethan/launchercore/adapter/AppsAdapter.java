package com.threethan.launchercore.adapter;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.metadata.IconLoader;
import com.threethan.launchercore.util.App;
import com.threethan.launcher.R;
import com.threethan.launchercore.view.LcContainerView;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class AppsAdapter<VH extends AppsAdapter.AppViewHolder>
        extends ListAdapter<ApplicationInfo, VH> {

    private List<ApplicationInfo> fullAppList;
    protected final int itemLayoutResId;


    private static final DiffUtil.ItemCallback<ApplicationInfo> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ApplicationInfo oldItem, @NonNull ApplicationInfo newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ApplicationInfo oldItem, @NonNull ApplicationInfo newItem) {
            return oldItem.packageName.equals(newItem.packageName);
        }
    };

    public AppsAdapter(int itemLayoutResId) {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT);
        this.itemLayoutResId = itemLayoutResId;
    }

    public void refresh() {
        if (fullAppList == null) return;

        submitList(new ArrayList<>(fullAppList));
    }
    protected void setFullItems(List<ApplicationInfo> items) {
        if (items.equals(fullAppList)) return;
        fullAppList = items;
        refresh();
    }

    protected static class AppViewHolder extends RecyclerView.ViewHolder {
        public ViewGroup container;
        public View view;
        public ImageView imageView;
        public TextView textView;
        public ApplicationInfo app;
        public boolean banner = false;
        public boolean darkMode = true;
        public int createdType;
        boolean showName = true;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private final LinkedList<Runnable> onReadyQueue = new LinkedList<>();
        public void whenReady(Runnable runnable) {
            if (view == null) onReadyQueue.add(runnable);
            else runnable.run();
        }

        protected void onReady() {
            while (!onReadyQueue.isEmpty()) onReadyQueue.pop().run();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AppViewHolder that)) return false;
            if (app == null || that.app == null) return false;
            return Objects.equals(app.packageName, that.app.packageName);
        }

        @Override
        public int hashCode() {
            return app == null ? super.hashCode() : Objects.hashCode(app.packageName);
        }
    }

    protected ViewGroup newContainer(ViewGroup parent) {
        return new LcContainerView(parent.getContext());
    }
    protected LayoutInflater layoutInflater;
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        ViewGroup container = newContainer(parent);
        VH holder = newViewHolder(container);
        holder.container = container;
        holder.createdType = viewType;

        if (layoutInflater == null)
            layoutInflater = LayoutInflater.from(parent.getContext());

        View view = layoutInflater.inflate(itemLayoutResId, holder.container, true);
        holder.view = view;
        holder.imageView = view.findViewById(R.id.itemImage);
        holder.textView = view.findViewById(R.id.itemLabel);

        if (viewType == 2 && holder.imageView.getLayoutParams() instanceof ConstraintLayout.LayoutParams clp) {
            clp.dimensionRatio = "16:9";
            holder.banner = true;
        }

        final boolean darkMode = LauncherActivity.darkMode;
        holder.textView.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        holder.textView.setShadowLayer(6, 0, 0, darkMode ? Color.BLACK : Color.WHITE);
        holder.darkMode = darkMode;

        final boolean showName = holder.banner
                ? LauncherActivity.namesBanner : LauncherActivity.namesSquare;
        holder.textView.setVisibility(showName ? View.VISIBLE : View.GONE);
        holder.showName = showName;

        setupViewHolder(holder);
        holder.onReady();

        return holder;
    }

    protected VH newViewHolder(View itemView) {
        //noinspection unchecked
        return (VH) new AppViewHolder(itemView);
    }

    protected void setupViewHolder(VH holder) {}

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ApplicationInfo app = getItem(position);
        holder.app = app;
        holder.whenReady(() -> {
//
            // Offload everything possible to background thread
            executorService.submit(() -> {
                final boolean darkMode = LauncherActivity.darkMode;
                if (darkMode != holder.darkMode) {
                    holder.textView.post(() -> {
                        holder.textView.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
                        holder.textView.setShadowLayer(6, 0, 0, darkMode ? Color.BLACK : Color.WHITE);
                    });
                    holder.darkMode = darkMode;
                }

                final boolean showName = holder.banner
                        ? LauncherActivity.namesBanner : LauncherActivity.namesSquare;
                if (showName != holder.showName) {
                    holder.textView.post(() -> holder.textView.setVisibility(showName ? View.VISIBLE : View.GONE));
                    holder.showName = showName;
                }

                App.getLabel(app, label
                        -> {
                    if (holder.textView != null) onLabelChanged(holder, label);
                });
                IconLoader.loadIcon(holder.app, drawable -> {
                    if (holder.imageView != null) onIconChanged(holder, drawable);
                });
            });
            onViewHolderReady(holder);
        });
    }

    protected void onViewHolderReady(VH holder) {}

    private static final ExecutorService executorService = Core.EXECUTOR;
    protected void onIconChanged(VH holder, Drawable icon) {
        holder.imageView.post(() -> holder.imageView.setImageDrawable(icon));
    }
    protected void onLabelChanged(VH holder, String label) {
        holder.textView.post(() -> {
            if (!holder.textView.getText().toString().equals(label))
                holder.textView.setText(label, TextView.BufferType.NORMAL);
        });
    }

    public void notifyAllChanged() {
        try {
            notifyItemRangeChanged(0, getItemCount());
        } catch (Exception ignored) {}
    }

    @Override
    public long getItemId(int position) {
        if (getItem(position) == null) return super.getItemId(position);
        return Objects.hashCode(getItem(position).packageName); // Assuming this is unique!
    }

    @Override
    public ApplicationInfo getItem(int position) {
        return super.getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        return App.isBanner(getItem(position)) ? 2 : 1;
    }

    public void notifyItemChanged(ApplicationInfo ai) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).packageName.equals(ai.packageName)) {
                notifyItemChanged(i);
                return;
            }
        }
    }
}