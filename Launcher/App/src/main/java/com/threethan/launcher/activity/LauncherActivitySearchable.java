package com.threethan.launcher.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.Keyboard;
import com.threethan.launcher.helper.Launch;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.activity.view.EditTextWatched;

import java.util.Objects;

import eightbitlab.com.blurview.BlurView;

/**
    The class handles the additional interface elements and properties related to searching.
    It sends the current search term to the GroupsAdapter when updated, and open the first app
    when enter is pressed.

    This is the activity class that will actually be used; though its parent classes should operate
    fine if called independently.
 */

public class LauncherActivitySearchable extends LauncherActivityEditable {
    private boolean searching = false;
    protected void searchFor(String text) {
        Objects.requireNonNull(getAppAdapter()).filterBy(text);
        updateTopSearchResult();
    }
    BlurView searchBar;
    ObjectAnimator alphaIn;
    ObjectAnimator alphaOut;
    void showSearchBar() {
        try {
            clearTopSearchResult();
            searching = true;

            final int endMargin = 275;

            if (alphaIn  != null) alphaIn .end();
            if (alphaOut != null) alphaOut.end();
            alphaIn = ObjectAnimator.ofFloat(searchBar, "alpha", 1f);
            alphaOut = ObjectAnimator.ofFloat(topBar, "alpha", 0f);
            alphaIn.setDuration(100);
            alphaOut.setDuration(300);
            alphaIn.start();
            alphaOut.start();
            topBar.postDelayed(this::fixState, 300);
            searchBar.setVisibility(View.VISIBLE);

            searchBar.setOverlayColor(Color.parseColor(darkMode ? "#4A000000" : "#50FFFFFF"));

            searchBar.setClipToOutline(true);

            rootView.findViewById(R.id.blurViewSearchBar).setVisibility(View.VISIBLE);

            EditTextWatched searchText = findViewById(R.id.searchText);
            searchText.setTextColor(Color.parseColor(darkMode ? "#FFFFFF" : "#000000"));
            ((ImageView) findViewById(R.id.searchHintIcon)).setImageTintList(
                    ColorStateList.valueOf(Color.parseColor(darkMode ? "#FFFFFF" : "#000000")));
            ((ImageView) findViewById(R.id.searchCancelIcon)).setImageTintList(
                    ColorStateList.valueOf(Color.parseColor(darkMode ? "#FFFFFF" : "#000000")));

            ValueAnimator viewAnimator = ValueAnimator.ofInt(endMargin, 0);
            viewAnimator.setDuration(200);
            viewAnimator.setInterpolator(new DecelerateInterpolator());
            viewAnimator.addUpdateListener(animation -> {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) searchBar.getLayoutParams();
                lp.setMargins((int) animation.getAnimatedValue() + dp(25), 0, (int) animation.getAnimatedValue() + dp(25), 0);
                searchBar.setLayoutParams(lp);
                searchBar.requestLayout();
            });
            viewAnimator.start();
            if (getCurrentFocus() != null) getCurrentFocus().clearFocus();
            searchText.setText("");
            searchText.post(searchText::requestFocus);
            if (Platform.isVr(this)) postDelayed(() -> {
                Keyboard.hide(this, searchBar);
                Keyboard.show(this);
            }, 50);

            ValueAnimator padAnimator = ValueAnimator.ofInt(appsView.getPaddingTop(), dp(75));
            padAnimator.setDuration(200);
            padAnimator.setInterpolator(new DecelerateInterpolator());
            padAnimator.addUpdateListener(animation -> appsView.setPadding(appsView.getPaddingLeft(),
                    (Integer) animation.getAnimatedValue(),
                    appsView.getPaddingRight(),appsView.getPaddingBottom()));
            padAnimator.start();


        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    void hideSearchBar() {
        try {
            searching = false;
            Keyboard.hide(this, mainView);

            if (alphaIn != null) alphaIn.end();
            if (alphaOut != null) alphaOut.end();
            alphaIn = ObjectAnimator.ofFloat(topBar, "alpha", 1f);
            alphaOut = ObjectAnimator.ofFloat(searchBar, "alpha", 0f);
            alphaIn.setDuration(100);
            alphaOut.setDuration(300);
            alphaIn.start();
            alphaOut.start();
            searchBar.postDelayed(() -> searchBar.setVisibility(View.GONE), 250);

            searchBar.postDelayed(this::fixState, 300);
            topBar.setVisibility(groupsEnabled ? View.VISIBLE : View.GONE);
            refreshAdapters();

        } catch (NullPointerException ignored) {}
        clearTopSearchResult();

    }
    protected void fixState() {
        try {
            if (alphaIn != null) alphaIn.end();
            if (alphaOut != null) alphaOut.end();
            searchBar.setVisibility(searching ? View.VISIBLE : View.GONE);
            topBar.setVisibility(!searching ? (groupsEnabled ? View.VISIBLE : View.GONE) : View.GONE);
            searchBar.setAlpha(searching ? 1F : 0F);
            topBar.post(() -> topBar.setAlpha(1F)); // Prevent flicker on start
        } catch (NullPointerException ignored) {}
    }

    @Override
    public void refreshInterface() {
        searching = false;

        hideSearchBar();
        fixState();
        super.refreshInterface();
    }

    @Override
    protected void init() {
        super.init();
        // Set logo button
        View searchIcon = rootView.findViewById(R.id.searchIcon);
        searchIcon.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER)
                searchIcon.setBackgroundResource(R.drawable.bkg_hover_button_bar_hovered);
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT)
                searchIcon.setBackground(null);
            return false;
        });
        searchBar = rootView.findViewById(R.id.blurViewSearchBar);


        // It seems like the normal method for showing the keyboard doesn't work on quest
        // so we'll put an invisible EditText above the search button and give it a frame
        // to take focus and show the keyboard.
        // Afterwards, focus is moved to the actual search box via showSearchBar()
        searchIcon.setOnFocusChangeListener((v, hasFocus) -> searchIcon.post(() -> {
            if (hasFocus) {
                if (searching) hideSearchBar();
                else showSearchBar();
            }
        }));
        View searchBg = rootView.findViewById(R.id.blurViewSearchIcon);
        searchBg.setOnClickListener((v) -> showSearchBar());

        EditTextWatched searchText = findViewById(R.id.searchText);
        searchText.setOnEdited(this::searchFor);

        searchText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
            keyCode == KeyEvent.KEYCODE_ENTER) {
                // Launch the first visible icon when enter is pressed
                updateTopSearchResult();
                if (currentTopSearchResult != null) try {
                    Keyboard.hide(this, searchBg);
                    Launch.launchApp(this, currentTopSearchResult);
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
            return false;
        });

        searchText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && searching) {
                Keyboard.show(this);
                updateTopSearchResult();
            } else {
                Keyboard.hide(this, mainView);
                clearTopSearchResult();
            }
        });

        findViewById(R.id.searchCancelIcon).setOnClickListener(v -> hideSearchBar());

        searching = false;
    }
    private void
    updateTopSearchResult() {
        EditTextWatched searchText = findViewById(R.id.searchText);
        if (searchText == null) clearTopSearchResult();
        else if (searchText.getText().toString().isEmpty()) clearTopSearchResult();
        else {
            // Highlight top result
            if (getAppAdapter() != null && getAppAdapter().getItemCount() > 0)
                changeTopSearchResult(getAppAdapter().getItem(0));
            else clearTopSearchResult();
        }
    }
    private void clearTopSearchResult() {
        if (currentTopSearchResult == null) return;
        clearFocusPackageNames.add(currentTopSearchResult.packageName);
        currentTopSearchResult = null;
    }
    private void changeTopSearchResult(ApplicationInfo val) {
        if (currentTopSearchResult != null)
            clearFocusPackageNames.add(currentTopSearchResult.packageName);
        currentTopSearchResult = val;
    }

    /** @noinspection deprecation*/
    @Override
    public void onBackPressed() {
        if (searching) hideSearchBar();
        else super.onBackPressed();
    }

    @Override
    protected boolean getSearching() {
        return searching;
    }
}
