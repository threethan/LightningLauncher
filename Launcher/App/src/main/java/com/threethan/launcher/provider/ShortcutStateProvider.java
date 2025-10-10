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
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.Platform;

public class ShortcutStateProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        String[] columnNames = new String[]{"isOpen", "shouldBlur"};
        MatrixCursor cursor = new MatrixCursor(columnNames);

        // Column 0: isOpen: Has foreground instance
        final boolean isOpen = LauncherActivity.getForegroundInstance() != null
                && LauncherActivity.getForegroundInstance().isActive;

        boolean shouldBlur = false;
        try {
            // Column 1: shouldBlur: Should launch Quest 3 blur activity
            final DataStoreEditor dse = Platform.isQuestGen3()
                    ? SyncCoordinator.getDefaultDataStore(Core.context())
                    : null;
            shouldBlur = dse != null
                    && dse.getBoolean(Settings.KEY_BACKGROUND_BLUR, Settings.DEFAULT_BACKGROUND_BLUR);
        } catch (Exception ignored) {}

        cursor.addRow(new Object[]{isOpen ? 1 : 0, shouldBlur ? 1 : 0});
        return cursor;
    }


    @Override public String getType(@NonNull Uri uri) { return "img/webp"; }
    @Override public Uri insert(@NonNull Uri uri, ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
