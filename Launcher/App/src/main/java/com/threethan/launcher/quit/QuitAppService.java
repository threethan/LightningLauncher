package com.threethan.launcher.quit;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Closes the app that is currently running on Quest by pressing the system "Quit" button
 * in Meta's menu. Apps cannot close other apps directly, so this service finds that button
 * through accessibility and clicks it.
 * <p>
 * Flow: {@link #requestQuit()} starts a short search window. The service looks for the
 * Quit button in the visible system windows; if it is not there, it asks the system to show
 * its menu (the same as pressing the Meta button) and keeps looking until the deadline.
 * Everything it sees is written to logcat (tag {@value #TAG}) to help adjust the matching.
 */
public class QuitAppService extends AccessibilityService {
    private static final String TAG = "QuitHelper";
    private static final long SEARCH_WINDOW_MS = 6000;
    private static final long RETRY_INTERVAL_MS = 250;
    private static final long OPEN_MENU_AFTER_MS = 600;
    private static final long REOPEN_DELAY_MS = 900;
    private static final long AFTER_HOME_SEARCH_MS = 1500;

    /** Labels Meta uses for the button that closes the running app */
    private static final String[] QUIT_LABELS = {"quit", "quit app", "close app", "exit", "exit app"};

    @Nullable private static QuitAppService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long deadline = 0;
    private long startedAt = 0;
    private boolean openedMenu = false;
    private boolean loggedTree = false;

    /** @return True if the user has enabled this service */
    public static boolean isEnabled() {
        return instance != null;
    }

    /**
     * Starts looking for Meta's Quit button and presses it.
     * @return False if the service is not enabled
     */
    public static boolean requestQuit() {
        if (instance == null) return false;
        instance.startSearch();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Quit helper connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private void startSearch() {
        startedAt = SystemClock.elapsedRealtime();
        deadline = startedAt + SEARCH_WINDOW_MS;
        openedMenu = false;
        loggedTree = false;
        Log.i(TAG, "Quit requested");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::tick, 300);
    }

    private boolean searching() {
        return SystemClock.elapsedRealtime() < deadline;
    }

    private void tick() {
        if (!searching()) {
            // Don't reopen the launcher here: the user may have moved on (or closed it) by now
            Log.w(TAG, "Quit button not found before the deadline");
            logAllWindows();
            return;
        }
        if (tryPressQuit()) return;

        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        if (!openedMenu && elapsed >= OPEN_MENU_AFTER_MS) {
            openedMenu = true;
            Log.i(TAG, "Quit button not visible yet, opening the system menu");
            performGlobalAction(GLOBAL_ACTION_HOME);
            // From an immersive app, going home already closes it (seen on Horizon OS), so
            // only look for the Quit button a little longer, then come back to the launcher
            deadline = Math.min(deadline, SystemClock.elapsedRealtime() + AFTER_HOME_SEARCH_MS);
            handler.postDelayed(() -> {
                if (deadline == 0) return; // Quit was pressed; that path reopens the launcher
                deadline = 0;
                handler.removeCallbacksAndMessages(null);
                Log.i(TAG, "Went home; returning to the launcher");
                reopenLauncher();
            }, AFTER_HOME_SEARCH_MS);
        }
        handler.postDelayed(this::tick, RETRY_INTERVAL_MS);
    }

    /** Opens Lightning Launcher again */
    private void reopenLauncher() {
        try {
            android.content.Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent == null) return;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "Reopened launcher");
        } catch (Exception e) {
            Log.w(TAG, "Could not reopen launcher", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (searching() && tryPressQuit()) Log.i(TAG, "Pressed Quit after a window event");
    }

    @Override
    public void onInterrupt() {}

    /** Searches the visible system windows for the Quit button and clicks it */
    private boolean tryPressQuit() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (Exception e) {
            Log.w(TAG, "Could not read windows", e);
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            CharSequence pkg = root.getPackageName();
            // Only Meta's own windows: packageNames in the config filters events, not getWindows(),
            // and an "Exit" button in another app's panel must never be clicked
            if (pkg == null || !(pkg.toString().startsWith("com.oculus.")
                    || pkg.toString().startsWith("com.meta."))) continue;
            AccessibilityNodeInfo quit = findQuit(root);
            if (quit != null && click(quit)) {
                Log.i(TAG, "Pressed Quit in " + pkg);
                deadline = 0;
                handler.removeCallbacksAndMessages(null);
                // Bring Lightning Launcher back instead of leaving the system library open
                handler.postDelayed(this::reopenLauncher, REOPEN_DELAY_MS);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static AccessibilityNodeInfo findQuit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (matchesQuit(node.getText()) || matchesQuit(node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findQuit(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean matchesQuit(@Nullable CharSequence label) {
        if (label == null) return false;
        String s = label.toString().trim().toLowerCase(Locale.ROOT);
        for (String q : QUIT_LABELS) if (s.equals(q)) return true;
        return false;
    }

    /** Clicks the node, or its nearest clickable parent */
    private static boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        while (n != null) {
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            n = n.getParent();
        }
        return false;
    }

    /** Writes the labels of all visible windows to logcat, to help adjust the matching */
    private void logAllWindows() {
        if (loggedTree) return;
        loggedTree = true;
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                Log.i(TAG, "Window: " + window.getTitle() + " pkg="
                        + (root == null ? "?" : root.getPackageName()));
                if (root != null) logNode(root, 1);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not log windows", e);
        }
    }

    private static void logNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 25) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null || desc != null || node.isClickable()) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) indent.append("  ");
            Log.i(TAG, indent.toString() + node.getClassName()
                    + " text=" + text + " desc=" + desc
                    + " id=" + node.getViewIdResourceName()
                    + (node.isClickable() ? " [clickable]" : ""));
        }
        for (int i = 0; i < node.getChildCount(); i++) logNode(node.getChild(i), depth + 1);
    }
}
