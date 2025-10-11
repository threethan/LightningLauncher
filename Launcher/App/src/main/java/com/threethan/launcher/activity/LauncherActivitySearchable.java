package com.threethan.launcher.activity;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.threethan.launcher.R;
import com.threethan.launcher.activity.view.EditTextWatched;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.Keyboard;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
    The class handles the additional interface elements and properties related to searching.
    It sends the current search term to the GroupsAdapter when updated, and open the first app
    when enter is pressed.

    This is the activity class that will actually be used; though its parent classes should operate
    fine if called independently.
 */

public class LauncherActivitySearchable extends LauncherActivityEditable {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Core.init(this);
        super.onCreate(savedInstanceState);
    }
    private boolean searching = false;
    private boolean isNewSearch = true;

    protected void searchFor(String text) {
        Objects.requireNonNull(getAppAdapter()).filterBy(text, isNewSearch);
        isNewSearch = false;
    }
    Timer searchTimer = new Timer();
    private boolean beenNonEmpty = false;
    protected void queueSearch(String text) {
        int delay;
        searchTimer.cancel();
        switch (text.length()) {
            case 0:
                if (beenNonEmpty) hideSearchBar();
                return;
            case 1:
                delay = 600;
                break;
            case 2:
                delay = 300;
                break;
            case 3:
                delay = 200;
                break;
            case 4:
                delay = 150;
                break;
            default:
                delay = 100;
                break;
        }
        beenNonEmpty = true;
        searchTimer = new Timer();
        searchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    runOnUiThread(() -> searchFor(text));
                } catch (Exception ignored) {}
            }
        }, delay);
    }
    View searchBar;

    void showSearchBar() {
        beenNonEmpty = false;
        try {
            searching = true;
            searchBar.setAlpha(0F);

            ObjectAnimator alphaIn = ObjectAnimator.ofFloat(searchBar, "alpha", 1f);
            ObjectAnimator alphaOut = ObjectAnimator.ofFloat(topBar, "alpha", 0f);
            alphaIn.setDuration(200);
            alphaOut.setDuration(250);
            alphaIn.start();
            alphaOut.start();
            searchBar.setVisibility(View.VISIBLE);

            searchBar.setClipToOutline(true);

            rootView.findViewById(R.id.blurViewSearchBar).setVisibility(View.VISIBLE);

            EditTextWatched searchText = findViewById(R.id.searchText);

            if (getCurrentFocus() != null) getCurrentFocus().clearFocus();
            searchText.setText("");
            searchText.post(searchText::requestFocus);
            Keyboard.show(searchText);

        } catch (NullPointerException e) {
            Log.w(TAG, "NPE when showing searchbar", e);
        }
    }

    void hideSearchBar() {
        try {
            searching = false;
            isNewSearch = true;

            Keyboard.hide(mainView);

            searchBar.setVisibility(View.GONE);

            topBar.setVisibility(View.VISIBLE);
            topBar.setAlpha(1F);
            topBar.postDelayed(this::fixState, 500);
            refreshAdapters();

        } catch (NullPointerException ignored) {}
        searchFor("");

    }
    protected void fixState() {
        try {
            searchBar.setVisibility(searching ? View.VISIBLE : View.GONE);
            topBar.setVisibility(!searching ? View.VISIBLE : View.GONE);
            searchBar.setAlpha(searching ? 1F : 0F);
            topBar.post(() -> topBar.setAlpha(1F)); // Prevent flicker on start
        } catch (NullPointerException ignored) {}
    }

    @Override
    public void refreshInterface() {
        searching = false;
        isNewSearch = true;

        if (searchBar.getVisibility() == View.VISIBLE) {
            hideSearchBar();
            fixState();
        }
        super.refreshInterface();
    }

    @Override
    protected void init() {
        super.init();

        // Set logo button
        View searchIcon = rootView.findViewById(R.id.searchIcon);
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
        searchText.setOnEdited(this::queueSearch);

        searchText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
            keyCode == KeyEvent.KEYCODE_ENTER) {
                // Launch the first visible icon when enter is pressed
                if (getAppAdapter() != null && getAppAdapter().getTopSearchResult() != null) {
                    Keyboard.hide(searchBg);
                    LaunchExt.launchApp(this, getAppAdapter().getTopSearchResult());
                    return true;
                }
            }
            return false;
        });

        searchText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && searching) {
                Keyboard.show(searchText);
            } else {
                Keyboard.hide(mainView);
            }
        });

        findViewById(R.id.searchCancelIcon).setOnClickListener(v -> hideSearchBar());

        searching = false;
        isNewSearch = true;
    }

    @Override
    public void handleBackPressed() {
        if (searching) hideSearchBar();
        else super.handleBackPressed();
    }

    @Override
    protected boolean getSearching() {
        return searching;
    }
}
