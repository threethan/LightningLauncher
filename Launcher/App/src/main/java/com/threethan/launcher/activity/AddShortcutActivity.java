package com.threethan.launcher.activity;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.activity.support.DataStoreEditor;
import com.threethan.launcher.helper.Icon;
import com.threethan.launcher.helper.Platform;

import java.io.IOException;

/**
    Allows 3rd party apps to add shortcuts to the launcher. Not extensively tested!
 */
public class AddShortcutActivity extends Activity {
    /** Creates the activity and adds the shortcut to the launcher's DataStore */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        super.onCreate(savedInstanceState);
        LauncherApps launcherApps = (LauncherApps) this.getSystemService(Context.LAUNCHER_APPS_SERVICE);

        LauncherApps.PinItemRequest pinItemRequest = launcherApps.getPinItemRequest(getIntent());

        if (pinItemRequest == null) {
            this.finish();
            return;
        }

        ShortcutInfo shortcutInfo = pinItemRequest.getShortcutInfo();

        if (shortcutInfo == null) return;

        Drawable iconDrawable = launcherApps.getShortcutIconDrawable(shortcutInfo, 0);

        try {
            pinItemRequest.accept();
        } catch (RuntimeException ignored) {
            return;
        }

        String label = "";

        if (shortcutInfo.getShortLabel() != null) {
            label = shortcutInfo.getShortLabel().toString();
        } else if (shortcutInfo.getLongLabel() != null) {
            label = shortcutInfo.getLongLabel().toString();
        }

        String json = getFixedGsonWriter().toJson(shortcutInfo);
        DataStoreEditor dataStoreEditor = Compat.getDataStore(this);
        String url = Platform.addWebsite(dataStoreEditor, json, label);

        ApplicationInfo app = new ApplicationInfo();
        app.packageName = url;
        Icon.saveIconDrawableExternal(this, iconDrawable, app);
        this.finish();
    }
    /** Launches a given shortcut (if supported by the system) */
    public static void launchShortcut(Activity activity, String json) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;

        LauncherApps launcherApps = (LauncherApps) activity.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        Log.v("JSON", json);
        ShortcutInfo shortcutInfo = getFixedGsonReader().fromJson(json, ShortcutInfo.class);
        launcherApps.startShortcut(shortcutInfo, null, null);
    }
    /** Gets a Gson writer that works around duplicate field errors */
    protected static Gson getFixedGsonWriter() {
        return new GsonBuilder()
                .setExclusionStrategies(getExclusionStrategy()).create();
    }
    /** Gets a Gson reader that works around duplicate field & unwritable value errors */
    protected static Gson getFixedGsonReader() {
        return new GsonBuilder()
                .setExclusionStrategies(getExclusionStrategy())
                .registerTypeAdapter(CharSequence.class, new TypeAdapter<CharSequence>() {
                    @Override
                    public void write(JsonWriter out, CharSequence value) throws IOException {
                        out.value(value.toString());
                    }

                    @Override
                    public CharSequence read(JsonReader in) throws IOException {
                        return in.nextString();
                    }
                }).create();
    }
    /** Gets the exclusion strategy for working Gson reader/writer */
    protected static ExclusionStrategy getExclusionStrategy() {
        return new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return f.getName().equals("mChangingConfigurations");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        };
    }
}