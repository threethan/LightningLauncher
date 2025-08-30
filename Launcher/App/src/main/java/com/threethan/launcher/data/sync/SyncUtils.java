package com.threethan.launcher.data.sync;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.threethan.launchercore.Core;

import java.util.List;

public abstract class SyncUtils {
    public static final List<String> PACKAGE_NAMES = List.of(
            "com.threethan.launcher",
            "com.threethan.launcher.playstore",
            "com.threethan.launcher.metastore"
    );

    public static Bitmap queryInstances(@NonNull String type, @NonNull String packageName) {
        for (String pkg : PACKAGE_NAMES) {
            try {
                Uri uri = Uri.parse("content://" + pkg + ".sharedImageProvider");
                try (Cursor cursor = Core.context().getContentResolver()
                        .query(uri, null, null,
                                new String[]{type, packageName}, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        boolean valid = cursor.getInt(0) != 0;
                        if (valid) {
                            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(cursor.getBlob(1), 0, cursor.getBlob(1).length);
                            return bitmap;
                        }
                    }
                }
            } catch (Exception e) {
                // Log the error or handle it as needed
                e.printStackTrace();
            }
        }
        return null;
    }
}
