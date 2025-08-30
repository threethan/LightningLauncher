package com.threethan.launcher.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.threethan.launchercore.Core;

import java.io.IOException;

public class QuestAppMenuProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "image/png";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        try (AssetFileDescriptor afd = Core.context().getAssets().openFd("thumb.png")){
            return ParcelFileDescriptor.dup(afd.createInputStream().getFD());
        } catch (IOException e) {
            return null;
        }
    }
}
