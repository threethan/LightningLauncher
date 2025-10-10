package com.threethan.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Listens for specific accessibility events and opens the launcher appropriately */
@SuppressLint("AccessibilityPolicy")
public class ShortcutAccessibilityService extends AccessibilityService {
    private static final int HOVER_DELAY_MS = 500;
    private static final int LAUNCH_COOLDOWN_MS = 250;
    private static final int LAUNCH_COOLDOWN_EXTENDED_MS = 1750;
    private static final List<String> TARGET_PACKAGES = List.of(
            "com.threethan.launcher",
            "com.threethan.launcher.metastore",
            "com.threethan.launcher.playstore"
    );

    /**
     * Times how long you continue to hover an icon.
     * If you hold for at least HOVER_DELAY_MS, then it will launch.
     * When you exit the hover, the timer is cleared.
     */
    private static Timer hoverHoldTimer = null;

    /**
     * Times how much time passes between launch requests.
     * At least LAUNCH_COOLDOWN_MS must pass for a new launch to occur,
     * and no new hover events will be recognized until it completes
     */
    private static Timer launchCooldownTimer = null;

    public void onAccessibilityEvent(AccessibilityEvent event) {
        String eventText = event.getText().toString();
        String targetName = getResources().getString(R.string.target_name);
        String targetNameAlt = getResources().getString(R.string.target_name_alt);

        // Return if event does not contain expected text (in text or contentDescription)
        if (eventText.compareTo("[]") == 0) {
            if (event.getContentDescription() == null)
                return;
            if (targetName.compareToIgnoreCase(event.getContentDescription().toString()) != 0
            &&  targetNameAlt.compareToIgnoreCase(event.getContentDescription().toString()) != 0)
                return;
        } else {
            String targetEventName = "[" + targetName + "]";
            String targetEventNameAlt = "[" + targetNameAlt + "]";
            if (targetEventName.toLowerCase().compareToIgnoreCase(eventText) != 0
            &&  targetEventNameAlt.toLowerCase().compareToIgnoreCase(eventText) != 0)
                return;
        }

        // Now we know the event is valid, so handle it:
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
            // Handle Hover Start
            if (hoverHoldTimer == null && launchCooldownTimer == null) {
                hoverHoldTimer = new Timer();
                hoverHoldTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        trigger();
                        try {launchCooldownTimer.cancel();} catch (Exception ignored) {}
                        launchCooldownTimer = new Timer();
                    }
                }, HOVER_DELAY_MS);
            }
        } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT) {
            // Handle Hover End
            try {
                hoverHoldTimer.cancel();} catch (Exception ignored) {}
            hoverHoldTimer = null;
            if (launchCooldownTimer != null) setLaunchCooldownTimer(false);
        } else {
            // Handle click or window open (indicates a definitely intentional interaction)
            try {
                hoverHoldTimer.cancel();} catch (Exception ignored) {}
            if (launchCooldownTimer == null) {

                boolean useExtendedDelay
                        = event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED;
                launchCooldownTimer = new Timer();
                setLaunchCooldownTimer(useExtendedDelay);
                trigger();
            }
        }
    }

    /** Set a timer that, while active, prevents a new launch */
    private static void setLaunchCooldownTimer(boolean extended) {
        launchCooldownTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                launchCooldownTimer = null;
            }
        }, extended ? LAUNCH_COOLDOWN_EXTENDED_MS : LAUNCH_COOLDOWN_MS);
    }

    /** Trigger the launcher appropriately */
    private void trigger() {
        List<String> targets = TARGET_PACKAGES.stream().filter(pkg ->
                // Check if package exists
                getPackageManager().getLaunchIntentForPackage(pkg) != null
        );
        for (String packageName : targets) {
            if (triggerPackage(packageName, false)) break; // Stop after first successful launch
        }
        Log.w("LightningLauncherService", "No eligible target found, ignoring preference");
        for (String packageName : targets) {
            if (triggerPackage(packageName, true)) break; // Stop after first successful launch
        }
    }

    /** Launches a wrapped instance of Lightning Launcher if/as appropriate.
     * @param packageName The package to launch
     * @param ignoreShortcutPreference If true, will launch even if the app is blocking shortcuts
     * @return True if the app is not blocking shortcuts  */
    private boolean triggerPackage(String packageName, boolean ignoreShortcutPreference) {
        Log.i("LightningLauncherService", "Triggered from accessibility event");

        // Check if package even exists
        if (getPackageManager().getLaunchIntentForPackage(packageName) == null) {
            return false;
        }

        Uri uri = Uri.parse("content://"+packageName+".shortcutStateProvider");
        try {
            // Attempt to gain permission to use the provider when the launcher is closed
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {}
        try (
                Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)
        ){

            assert cursor != null;
            cursor.moveToFirst();

            try {
                int allowShortcutsIndex = cursor.getColumnIndex("allowShortcuts");
                int allowShortcuts = cursor.getInt(allowShortcutsIndex);
                if (allowShortcuts == 0) return false; // App is blocking shortcuts
            } catch (Throwable ignored) {
            } // Old versions don't have this column

            int isOpenIndex = cursor.getColumnIndex("isOpen");
            int shouldBlurIndex = cursor.getColumnIndex("shouldBlur");

            boolean currentlyOpen = cursor.getInt(isOpenIndex) != 0;
            if (currentlyOpen) return true; // Don't launch if already open
            boolean shouldBlur = cursor.getInt(shouldBlurIndex) != 0;

            launchPackage(shouldBlur, packageName);
            return true;
        } catch (Throwable e) {
            // Old versions don't have the provider
            Log.w("LightningLauncherService", "Error reading state", e);
            launchPackage(false, packageName); // Fallback to non-blurred launch
            return true;
        }
    }

    /** Launch LightningLauncher with the appropriate intent */
    private void launchPackage(boolean shouldBlur, String packageName) {
        MainActivity.packageToOpen = packageName;

        Intent launchIntent = new Intent(this,
                shouldBlur ? MainActivityBlur.class : MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        Log.i("LightningLauncherService", "Opening launcher activity from accessibility event");
        startActivity(launchIntent);
    }

    public void onInterrupt() {}
}