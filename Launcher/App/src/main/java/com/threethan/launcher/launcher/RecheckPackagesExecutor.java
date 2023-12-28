package com.threethan.launcher.launcher;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.threethan.launcher.helper.Platform;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
    RecheckPackagesTask

    This task checks a list of installed packages asynchronously, then check if it differs from
    those known by the LauncherActivity which calls it. If so (package installed/uninstalled),
    it tells the LauncherActivity to reload it's list of packages with metadata.
 */

class RecheckPackagesExecutor {
    public void execute(LauncherActivity owner) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {

            PackageManager packageManager = owner.getPackageManager();
            List<ApplicationInfo> foundApps = packageManager.getInstalledApplications(0);

            boolean changeFound = Platform.installedApps == null ||
                    Platform.installedApps.size() != foundApps.size();
            if (changeFound) {
                owner.runOnUiThread(() -> {
                    owner.reloadPackages();
                    owner.refreshInterface();
                });
            }
        });
    }
}