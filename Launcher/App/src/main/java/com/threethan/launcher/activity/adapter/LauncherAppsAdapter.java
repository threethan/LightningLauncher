package com.threethan.launcher.activity.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BaseInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.threethan.launcher.BuildConfig;
import com.threethan.launcher.R;
import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.dialog.AppDetailsDialog;
import com.threethan.launcher.activity.support.SettingsManager;
import com.threethan.launcher.activity.support.SortHandler;
import com.threethan.launcher.activity.view.FocusDirectionAwareContainer;
import com.threethan.launcher.activity.view.LauncherAppImageView;
import com.threethan.launcher.activity.view.LauncherAppListContainer;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launcher.helper.PlaytimeHelper;
import com.threethan.launchercore.adapter.AppsAdapter;
import com.threethan.launchercore.lib.StringLib;
import com.threethan.launchercore.util.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 *     The adapter for the main app grid.
 * <p>
 *     Only app views matching the current search term or group filter will be shown.
 *     When a view is requested for an app, it gets cached, and will be reused if displayed again.
 *     This massively speeds up group-switching and makes search possible without immense lag.
 * <p>
 *     This class also handles clicking and long clicking apps, including the app settings dialog.
 *     It also handles displaying/updating the views of an app (hover interactions, background website)
 */
public class LauncherAppsAdapter extends AppsAdapter<LauncherAppsAdapter.AppViewHolderExt> {
    private LauncherActivity launcherActivity;
    private Set<ApplicationInfo> fullAppSet;
    protected ApplicationInfo topSearchResult = null;
    private LauncherAppListContainer container;
    private SortHandler.SortMode sortMode = SortHandler.SortMode.STANDARD;
    private Runnable onListReadyEveryTime;
    private Runnable onListReadyOneShot;

    private boolean getEditMode() {
        return launcherActivity.isEditing();
    }
    public LauncherAppsAdapter(LauncherActivity activity) {
        super(R.layout.item_app);
        launcherActivity = activity;
    }

    public void setFullAppSet(Set<ApplicationInfo> myApps) {
        fullAppSet = myApps;
    }

    public synchronized void setSortMode(SortHandler.SortMode mode) {
        if (mode == sortMode) return;
        sortMode = mode;
        setAppList(launcherActivity);
    }

    public synchronized void setOnListReadyOneShot(Runnable onListReadyOneShot) {
        this.onListReadyOneShot = onListReadyOneShot;
    }
    public synchronized void setOnListReadyEveryTime(Runnable onListReadyEveryTime) {
        this.onListReadyEveryTime = onListReadyEveryTime;
    }
    public synchronized void setAppList(LauncherActivity activity) {
        SettingsManager settingsManager = SettingsManager.getInstance(activity);
        launcherActivity = activity;

        topSearchResult = null;
        prevFilterText = "";

        SortHandler.getVisibleAppsSorted(
                settingsManager,
                true,
                fullAppSet,
                sortMode,
                list -> setFullItems(Collections.unmodifiableList(list))
        );

        layoutInflater = activity.getLayoutInflater();
    }

    private static String prevFilterText = "";
    public synchronized void filterBy(String text, boolean newSearch) {
        if (text.isEmpty()) {
            prevFilterText = "";
            updateAppFocus(null, true, FocusSource.SEARCH);
            refresh();
            return;
        }

        SettingsManager settingsManager = SettingsManager.getInstance(launcherActivity);

        boolean showHidden = !text.isEmpty() && launcherActivity.dataStoreEditor.getBoolean(
                Settings.KEY_SEARCH_HIDDEN, Settings.DEFAULT_SEARCH_HIDDEN);

        Consumer<List<ApplicationInfo>> onSearchableListReady = newItems -> {
            newItems.removeIf(new SortableFilterPredicate(text).negate());


            if (!showHidden) {
                Set<String> hg = SettingsManager.getGroupAppsMap().get(Settings.HIDDEN_GROUP);
                if (hg != null) newItems.removeIf(ai -> hg.contains(ai.packageName));
            }

            boolean showWeb = !text.isEmpty() && launcherActivity.dataStoreEditor
                    .getBoolean(Settings.KEY_SEARCH_WEB, Settings.DEFAULT_SEARCH_WEB);

            // Add search queries
            if (showWeb && !launcherActivity.isEditing()) {
                // Remove existing search entries
                newItems.removeIf(app
                        -> app.packageName != null && StringLib.isSearchUrl(app.packageName));

                final ApplicationInfo googleProxy = new ApplicationInfo();
                googleProxy.packageName = StringLib.googleSearchForUrl(text);
                newItems.add(googleProxy);

                final ApplicationInfo youTubeProxy = new ApplicationInfo();
                youTubeProxy.packageName = StringLib.youTubeSearchForUrl(text);
                newItems.add(youTubeProxy);

                final ApplicationInfo apkMirrorProxy = new ApplicationInfo();
                apkMirrorProxy.packageName = StringLib.apkMirrorSearchForUrl(text);
                newItems.add(apkMirrorProxy);
            }

            topSearchResult = newItems.isEmpty() ? null : newItems.get(0);
            submitList(new ArrayList<>(newItems), () -> notifyItemChanged(topSearchResult));
        };

        boolean reList = !text.startsWith(prevFilterText) || newSearch;
        prevFilterText = text;

        if (reList) {
            SortHandler.getVisibleAppsSorted(
                    settingsManager,
                    false,
                    fullAppSet,
                    SortHandler.SortMode.SEARCH,
                    onSearchableListReady
            );
        } else {
            onSearchableListReady.accept(new ArrayList<>(getCurrentList()));
        }
    }
    public void setLauncherActivity(LauncherActivity val) {
        launcherActivity = val;
    }

    @Override
    public synchronized void submitList(@Nullable List<ApplicationInfo> list) {
        if (container != null) {
            container.setAllowLayout(false);
        }
        super.submitList(list, () -> {
            if (container != null) {
                container.setAllowLayout(true);
            }
            if (onListReadyEveryTime != null) onListReadyEveryTime.run();
            if (onListReadyOneShot != null) {
                onListReadyOneShot.run();
                onListReadyOneShot = null;
            }
        });
    }


    public ApplicationInfo getTopSearchResult() {
        return topSearchResult;
    }

    public void setContainer(LauncherAppListContainer container) {
        this.container = container;
    }

    public void clearAppFocus() {
        updateAppFocus(null, true, FocusSource.CURSOR);
        updateAppFocus(null, true, FocusSource.SEARCH);
    }

    protected static class AppViewHolderExt extends AppsAdapter.AppViewHolder {
        Button moreButton;
        Button playtimeButton;
        boolean hovered = false;
        public AppViewHolderExt(@NonNull View itemView) {
            super(itemView);
        }
    }

    @Override
    protected AppViewHolderExt newViewHolder(View itemView) {
        return new AppViewHolderExt(itemView);
    }

    protected void setupViewHolder(AppViewHolderExt holder) {
        holder.moreButton = holder.view.findViewById(R.id.moreButton);
        holder.playtimeButton = holder.view.findViewById(R.id.playtimeButton);
        holder.imageView.setClipToOutline(true);

        // Launch app on click
        holder.view.setOnClickListener(view -> {
            if (holder.app != null && holder.app.packageName != null) {
                if (getEditMode()) {
                    boolean selected = launcherActivity.selectApp(holder.app.packageName);
                    holder.view.animate().alpha(selected ? 0.5f : 1).setDuration(150).start();
                } else {
                    boolean fullAnimation = LaunchExt.launchApp(launcherActivity, holder.app);
                    animateOpen(holder, fullAnimation);
                }
            }
        });
        holder.view.setOnLongClickListener(view -> {
            if (holder.app == null || holder.app.packageName == null) return false;
            if (getEditMode() || !launcherActivity.canEdit() || launcherActivity.dataStoreEditor
                    .getBoolean(Settings.KEY_DETAILS_LONG_PRESS, Settings.
                            DEFAULT_DETAILS_LONG_PRESS)) {
                new AppDetailsDialog(launcherActivity, holder.app).show();
            } else {
                launcherActivity.setEditMode(true);
                launcherActivity.selectApp(holder.app.packageName);
                holder.view.animate().alpha(0.5f).setDuration(300).start();
            }
            return true;
        });

        // Hover
        View.OnHoverListener hoverListener = (view, event) -> {
            boolean hovered;
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) hovered = true;
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                for (View subView : new View[] {holder.playtimeButton, holder.moreButton})
                    if (view != subView && subView != null && subView.isHovered()) return false;
                hovered = false;
            } else {
                if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
                    // Depth effect on hover
                    float oy = view == holder.view ? 0f : view.getY();
                    float ox = view == holder.view ? 0f : view.getX();
                    float y = (event.getY() + oy - holder.view.getHeight() / 2f);
                    float x = (event.getX() + ox - holder.view.getWidth() / 2f);
                    holder.view.setRotationY(-x / holder.view.getHeight() * -5);
                    holder.view.setRotationX(y / holder.view.getHeight() * -5);
                    holder.imageView.invalidate(); // redraw parallax
                }
                return false;
            }
            if (view == holder.view || event.getAction() == MotionEvent.ACTION_HOVER_ENTER)
                updateAppFocus(holder, hovered, FocusSource.CURSOR);
            return false;
        };

        holder.view.setOnHoverListener(hoverListener);
        holder.moreButton.setOnHoverListener(hoverListener);
        holder.playtimeButton.setOnHoverListener(hoverListener);
        holder.view.setOnFocusChangeListener((view, hasFocus) -> {
            updateAppFocus(holder, hasFocus, FocusSource.CURSOR);
            if (LauncherActivity.shouldReduceMotion()) return;
            if (view instanceof FocusDirectionAwareContainer fView) {
                Consumer<Integer> animate = (fDir) -> {
                    final float intensity = 6f;
                    int xSign = 0;
                    int ySign = 0;
                    switch (fDir) {
                        case View.FOCUS_UP -> ySign = -1;
                        case View.FOCUS_DOWN -> ySign = 1;
                        case View.FOCUS_LEFT -> xSign = -1;
                        case View.FOCUS_RIGHT -> xSign = 1;
                    }
                    float tension = hasFocus ? -1f : -3f;
                    if (hasFocus) {
                        xSign *= -1;
                        ySign *= -1;
                    }
                    view.animate().rotationX(ySign * intensity).rotationY(-xSign * intensity)
                            .setDuration(50).setInterpolator(new OvershootInterpolator(tension))
                            .withEndAction(() -> view.animate().rotationX(0).rotationY(0)
                                    .setDuration(200).setInterpolator(new OvershootInterpolator(tension))
                                    .start())
                            .start();
                    ValueAnimator va = ValueAnimator.ofFloat(0, 1);
                    va.setInterpolator(new OvershootInterpolator());
                    va.addUpdateListener(anim -> holder.imageView.invalidate());
                    va.setDuration(300);
                    va.start();
                };
                view.post(() -> {
                    int fDir = fView.getFocusDirection();
                    if (fDir != 0) {
                        lastKnownFocusDirection = fDir;
                        animate.accept(fView.getFocusDirection());
                    } else {
                        fView.post(() -> animate.accept(lastKnownFocusDirection));
                    }
                });
            }
        });


        holder.view.findViewById(R.id.moreButton).setOnClickListener(
                view -> new AppDetailsDialog(launcherActivity, holder.app).show());

        holder.view.findViewById(R.id.playtimeButton).setOnClickListener(v
                -> PlaytimeHelper.openFor(holder.app.packageName));
    }
    private static int lastKnownFocusDirection = 0;

    @Override
    protected void onViewHolderReady(AppViewHolderExt holder) {
        super.onViewHolderReady(holder);
        if (!Platform.isTv())
            holder.playtimeButton.post(() -> holder.playtimeButton.setText("--:--"));
        holder.view.post(() -> updateSelected(holder));
    }

    public void notifySelectionChange(String packageName) {
        for (int i=0; i<getItemCount(); i++)
            if (Objects.equals(getItem(i).packageName, packageName))
                notifyItemChanged(i, getItem(i));
    }

    private void updateSelected(AppViewHolderExt holder) {
        if (holder.app == null || holder.app.packageName == null) return;
        boolean selected = launcherActivity.isSelected(holder.app.packageName);
        if (selected != holder.view.getAlpha() < 0.9) {
            holder.view.animate().alpha(selected ? 0.5f : 1).setDuration(150).start();
        }

        // Top search result
        if (topSearchResult != null && holder.app != null
                && Objects.equals(topSearchResult.packageName, holder.app.packageName)) {
            updateAppFocus(holder, true, FocusSource.SEARCH);
        }
    }
    // Only one focused app is allowed per source at a time.
    private enum FocusSource { CURSOR, SEARCH }

    @Override
    protected ViewGroup newContainer(ViewGroup parent) {
        return new FocusDirectionAwareContainer(parent.getContext());
    }

    private final Map<FocusSource, AppViewHolderExt> focusedHolderBySource = new HashMap<>();
    /** @noinspection ClassEscapesDefinedScope*/
    public synchronized void updateAppFocus(AppViewHolderExt holder, boolean focused, FocusSource source) {
        // Handle focus sources
        if (focused) {
            AppViewHolderExt prevHolder = focusedHolderBySource.getOrDefault(source, null);
            focusedHolderBySource.put(source, holder);
            // If not focused by another source, remove prev. hover
            if (!focusedHolderBySource.containsValue(prevHolder) && prevHolder != null)
                updateAppFocus(prevHolder, false, source);
        } else if (focusedHolderBySource.containsValue(holder)) {
            focusedHolderBySource.remove(source);
            // Return early if focused by another source
            if (focusedHolderBySource.containsValue(holder)) return;
        }

        try {
            if (holder.hovered == focused) return;

            if (!Platform.isTv())
                holder.moreButton.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);

            //noinspection ConstantValue
            if (!Platform.isTv() && !BuildConfig.FLAVOR.equals("metastore")
                    && LauncherActivity.timesBanner) {
                if (focused) {
                    // Show and update view holder
                    holder.playtimeButton.setVisibility(holder.banner
                            && !holder.app.packageName.contains("://")
                            ? View.VISIBLE : View.INVISIBLE);
                    if (holder.banner
                            && !holder.app.packageName.contains("://")) {
                        PlaytimeHelper.getPlaytime(holder.app.packageName,
                                t -> launcherActivity.runOnUiThread(()
                                        -> holder.playtimeButton.setText(t)));
                    }
                } else holder.playtimeButton.setVisibility(View.INVISIBLE);
            }
            try {
                View view = holder.view;
                view.setZ(focused ? 2 : 1);
                holder.hovered = focused;
            } catch (Exception e) {
                Log.w("LauncherAppsAdapter", "Failed to update z-order", e);
            }

            if (LauncherActivity.shouldReduceMotion()) {
                holder.imageView.setForeground(focused ? holder.view.getContext().getDrawable(R.drawable.lc_fg_focused) : null);
                return;
            }

            final boolean tv = Platform.isTv();
            final float newScaleOuter = focused ? (tv ? 1.250f : 1.085f) : 1.005f;

            final float newElevation = focused ? 20f : 3f;
            final float newZ = focused ? (Platform.isTv() ? -0.5f : (holder.banner ? 1f :0f)) : 0f;

            final float textScale = 1 - (1 - (1 / newScaleOuter)) * 0.7f;
            final int duration = tv ? 175 : 250;
            BaseInterpolator interpolator = Platform.isTv() ?
                    new LinearInterpolator() : new OvershootInterpolator();

            holder.view.animate().scaleX(newScaleOuter).scaleY(newScaleOuter)
                    .setDuration(duration).setInterpolator(interpolator).start();
            if (!focused) {
                holder.view.animate().rotationX(0).rotationY(0)
                    .setDuration(duration).setInterpolator(new OvershootInterpolator(-3)).start();
            }
            holder.moreButton.animate().alpha(focused ? 1f : 0f)
                    .setDuration(duration).setInterpolator(interpolator).start();
            holder.textView.animate().scaleX(textScale).scaleY(textScale)
                    .setDuration(duration).setInterpolator(interpolator).start();

            ObjectAnimator aE = ObjectAnimator.ofFloat(holder.imageView, "elevation",
                    newElevation);
            ObjectAnimator tz = ObjectAnimator.ofFloat(holder.imageView, "translationZ", newZ);

            aE.setInterpolator(interpolator);
            tz.setInterpolator(interpolator);

            aE.setDuration(duration).start();
            tz.setDuration(duration).start();

            boolean banner = holder.banner;
            if (banner && !LauncherActivity.namesBanner || !banner && !LauncherActivity.namesSquare)
                holder.textView.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);

            if (holder.imageView instanceof LauncherAppImageView li)
                li.setTranslationParent(holder.view);

            // Force correct state, even if interrupted
            holder.view.postDelayed(() -> {
                if (Objects.equals(focusedHolderBySource.get(source), holder)) {
                    holder.view.setScaleX(newScaleOuter);
                    holder.view.setScaleY(newScaleOuter);
                    holder.imageView.setElevation(newElevation);
                    holder.imageView.setTranslationZ(newZ);
                    holder.view.setActivated(false);
                }
            }, tv ? 200 : 300);
        } catch (Exception ignored) {}
    }

    // Animation

    private void animateOpen(AppViewHolderExt holder, boolean fullAnimation) {
        // If reduced motion is enabled, use an alternate fade anim
        boolean srm = LauncherActivity.shouldReduceMotion();
        if (srm) fullAnimation = false;
        try {
            int[] l = new int[2];
            int[] lw = new int[2];
            holder.imageView.getLocationInWindow(l);
            launcherActivity.rootView.getLocationInWindow(lw);
            l[0] -= lw[0];
            l[1] -= lw[1];

            int w = holder.imageView.getWidth();
            int h = holder.imageView.getHeight();

            View openAnim = launcherActivity.rootView.findViewById(R.id.openAnim);

            ImageView openIcon = openAnim.findViewById(R.id.openIcon);
            openIcon.setImageDrawable(holder.imageView.getDrawable());
            openIcon.setBackground(holder.imageView.getBackground());
            openIcon.setAlpha(1F);
            openAnim.setScaleX(holder.view.getScaleX());
            openAnim.setScaleY(holder.view.getScaleY());
            openAnim.setX(l[0] + (w * holder.view.getScaleX() - w) / 2f);
            openAnim.setY(l[1] + (h * holder.view.getScaleY() - h) / 2f);
            ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(w, h);
            openAnim.setLayoutParams(layoutParams);

            openAnim.setVisibility(View.VISIBLE);
            openAnim.setAlpha(1F);
            openIcon.setClipToOutline(true);

            ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", fullAnimation ? 50f : 3f);
            ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", fullAnimation ? 50f : 3f);
            @SuppressLint("ObjectAnimatorBinding")
            ObjectAnimator aA = ObjectAnimator.ofFloat(fullAnimation ? openIcon : openAnim, "Alpha", 0f);
            aX.setDuration(fullAnimation ? 800 : 300);
            aY.setDuration(fullAnimation ? 800 : 300);
            aA.setDuration(fullAnimation ? 400 : 300);

            if (srm) {
                openAnim.setScaleX(1.1f);
                openAnim.setScaleY(1.1f);
                openAnim.setAlpha(0.5f);
            } else {
                aX.start();
                aY.start();
            }
            aA.start();

            ObjectAnimator aFade = ObjectAnimator.ofFloat(openAnim, "Alpha", 0f);
            aFade.setStartDelay(1000);
            aFade.setDuration(400);
            aFade.start();

            openAnim.postDelayed(() -> {
                openAnim.setVisibility(View.GONE);
                openIcon.setImageDrawable(null);
            }, 1000);
        } catch (Exception ignored) {}
    }
    public static void animateClose(LauncherActivity launcherActivity) {
        View openAnim = launcherActivity.rootView.findViewById(R.id.openAnim);
        openAnim.post(() -> openAnim.setVisibility(View.GONE));
    }
}