package com.threethan.launcher.activity.executor;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.activity.LauncherActivity;

import java.util.List;

/**
    This task checks a list of installed packages asynchronously, then check if it differs from
    those known by the LauncherActivity which calls it. If so (package installed/uninstalled),
    it tells the LauncherActivity to reload it's list of packages with metadata.
 */

public class RecheckPackagesExecutor {
    public void execute(LauncherActivity owner) {
        Thread thread = new Thread(() -> {
            PackageManager packageManager = owner.getPackageManager();
            List<ApplicationInfo> foundApps = packageManager.getInstalledApplications(0);

            if (Platform.installedApps == null) {
                Log.v("Lightning Launcher", "Package check called before initial load, " +
                        "will be ignored");
                return;
            }
            if (Platform.installedApps.size() != foundApps.size()) {
                owner.runOnUiThread(() -> {
                    Log.v("Lightning Launcher", "Package change detected!");
                    owner.refreshPackages();
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
}