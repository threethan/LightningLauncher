package com.threethan.launcher.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.data.sync.SyncCoordinator;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.Platform;

public class ShortcutStateProvider extends ContentProvider {
    /** How long after a close the unwanted trigger is still expected */
    private static final long CLOSE_TRIGGER_WINDOW_MS = 4000;
    /** The service queries twice per trigger, a few ms apart; block all queries in this burst */
    private static final long TRIGGER_BURST_MS = 1000;
    /** After the launcher opens quick settings, it still counts as open while not focused */
    private static final long SYSTEM_PANEL_GRACE_MS = 2000;
    private static long consumedCloseTime = 0;
    private static long blockUntil = 0;

    /** @return True if this query belongs to the first trigger after the launcher closed */
    private static synchronized boolean consumeCloseTrigger() {
        final long now = android.os.SystemClock.elapsedRealtime();
        final long closedAt = LauncherActivity.lastHiddenTime;
        if (closedAt != consumedCloseTime && now - closedAt < CLOSE_TRIGGER_WINDOW_MS) {
            consumedCloseTime = closedAt;
            blockUntil = now + TRIGGER_BURST_MS;
        }
        return now < blockUntil;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        String[] columnNames = new String[]{"isOpen", "shouldBlur", "allowShortcuts"};
        MatrixCursor cursor = new MatrixCursor(columnNames);

        // Column 2: isEnabled: Is the shortcut feature enabled for this variant
        try {
            boolean isEnable = SyncCoordinator.getDefaultDataStoreEditor(Core.context())
                    .getBoolean(Settings.KEY_ALLOW_SHORTCUTS, Settings.DEFAULT_ALLOW_SHORTCUTS);
            if (!isEnable) {
                cursor.addRow(new Object[]{0, 0, 0});
                return cursor;
            }
        } catch (Exception ignored) {}

        // Column 0: isOpen: Has foreground instance
        // Closing the launcher (or it closing itself after opening an app) makes the system show
        // its navigator, which fires the shortcut service once, 0.2-2 s later. That first burst of
        // queries is answered "open" so the launcher stays closed; later queries (the user pressing
        // Meta) are answered normally. A fixed time window cannot work here: the unwanted trigger
        // can come 2 s after closing while a real Meta press can come 2.4 s after.
        final boolean justClosed = consumeCloseTrigger();
        final boolean isOpen = justClosed || (LauncherActivity.getForegroundInstance() != null
                && LauncherActivity.getForegroundInstance().isActive
                && (LauncherActivity.getForegroundInstance().isTopResumed
                    || android.os.SystemClock.elapsedRealtime()
                        - LauncherActivity.lastSystemPanelOpen < SYSTEM_PANEL_GRACE_MS));
        android.util.Log.i("ShortcutState", "isOpen=" + isOpen + " justClosed=" + justClosed
                + " sinceClose=" + (android.os.SystemClock.elapsedRealtime() - LauncherActivity.lastHiddenTime));

        boolean shouldBlur = false;
        try {
            // Column 1: shouldBlur: Should launch Quest 3 blur activity
            final DataStoreEditor dse = Platform.isQuestGen3()
                    ? SyncCoordinator.getDefaultDataStoreEditor(Core.context())
                    : null;
            shouldBlur = dse != null
                    && PlatformExt.doesSupportBlendEffects()
                    && dse.getBoolean(Settings.KEY_BACKGROUND_BLUR, Settings.DEFAULT_BACKGROUND_BLUR);
        } catch (Exception ignored) {}

        cursor.addRow(new Object[]{isOpen ? 1 : 0, shouldBlur ? 1 : 0, 1});
        return cursor;
    }


    @Override public String getType(@NonNull Uri uri) { return "img/webp"; }
    @Override public Uri insert(@NonNull Uri uri, ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
