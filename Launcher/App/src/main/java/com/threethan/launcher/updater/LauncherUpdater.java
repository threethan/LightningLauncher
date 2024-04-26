package com.threethan.launcher.updater;

import android.app.Activity;

import com.threethan.launcher.activity.support.DataStoreEditor;

public class LauncherUpdater extends AppUpdater {
    private static final String GIT_REPO_LAUNCHER = "threethan/LightningLauncher";

    @Override
    protected String getAppDisplayName() {
        return "Lightning Launcher";
    }

    // URL Constants
    @Override
    protected String getAppDownloadName() {
        return "LightningLauncher"; // Name of apk on github, not including ".apk"
    }

    @Override
    protected String getGitRepo() {
        return GIT_REPO_LAUNCHER;
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

    public LauncherUpdater(Activity activity) {
        super(activity);
    }
}
