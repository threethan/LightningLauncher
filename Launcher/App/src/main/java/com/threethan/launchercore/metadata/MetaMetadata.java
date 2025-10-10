package com.threethan.launchercore.metadata;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** @noinspection unused*/
public class MetaMetadata {
    private static final String COMMON_URL
            = "https://raw.githubusercontent.com/threethan/MetaMetadata/main/data/common/%s.json";

    public static class App {
        final Map<String, String> data;
        protected App(Map<String, String> data) {
            this.data = data;
        }
        /** @return The app's actual display name */
        public String label() {
            return data.get("name");
        }

        /**
         * Gets an image of a certain type, and saves it to a file
         * (synchronous, must be called off UI thread)
         * @param type Image type ("landscape", "portrait", "square", "icon", or "hero")
         * @param saveFile File to save the image
         * @return A Bitmap if icon was downloaded successfully
         */
        public @Nullable Bitmap downloadImage(String type, File saveFile) {
            if (!data.containsKey(type)) return null;
            return IconUpdater.downloadIconFromUrl(data.get(type), saveFile);
        }
    }

    private static final Map<String, App> byPackage = new ConcurrentHashMap<>();

    /**
     * Gets app metadata for a given package (synchronous, must be called off UI thread)
     * @param packageName Package name of the app
     * @return metadata for the app
     */
    @Nullable public static App getForPackage(String packageName) {
        if (byPackage.containsKey(packageName))
            return byPackage.get(packageName);

        try {
            URL url = new URL(String.format(COMMON_URL, packageName));
            URLConnection request = url.openConnection();
            request.connect();

            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> data;
            try {
                data = new Gson()
                        .fromJson(new InputStreamReader((InputStream) request.getContent()), type);
            } catch (FileNotFoundException e) {
                return null;
            } catch (Exception e) {
                Log.e("MetaMetadata", "Gson error for "+packageName, e);
                return null;
            }

            App app = new App(data);
            byPackage.put(packageName, app);
            return app;
        } catch (IOException e) {
            return null;
        }
    }
}
