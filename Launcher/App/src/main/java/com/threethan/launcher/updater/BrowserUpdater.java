package com.threethan.launcher.updater;

import android.app.Activity;
import android.os.Build;

import com.threethan.launcher.activity.support.DataStoreEditor;

public class BrowserUpdater extends AppUpdater {
    public static final String GIT_REPO_BROWSER = "threethan/LightningBrowser";
    // Will be prompted to update from LL if version code is less than this
    public static final int REQUIRED_VERSION_CODE = 1011;

    // URL Constants
    @Override
    protected String getAppDownloadName() {
        // Name of apk on github, not including ".apk"
        return  (Build.SUPPORTED_64_BIT_ABIS.length > 0)
                ? "LightningBrowser_Arm64" : "LightningBrowser";
    }

    @Override
    protected String getAppPackageName() {
        return "com.threethan.browser";
    }

    @Override
    protected String getAppDisplayName() {
        return "Lightning Browser";
    }

    @Override
    protected String getGitRepo() {
        return GIT_REPO_BROWSER;
    }
    private static final String KEY_IGNORED_UPDATE_TAG = "IGNORED_UPDATE_TAG";
    @Override
    protected void putIgnoredUpdateTag(String ignoredUpdateTag) {
        new DataStoreEditor(activity).putString(KEY_IGNORED_UPDATE_TAG, ignoredUpdateTag);
    }
    @Override
    protected String getIgnoredUpdateTag() {
        return new DataStoreEditor(activity).getString(KEY_IGNORED_UPDATE_TAG, "");
    }

    public BrowserUpdater(Activity activity) {
        super(activity);
    }
}
