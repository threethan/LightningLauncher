package com.threethan.launcher.activity.chainload;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.Objects;

// These activities are used for advanced custom window sizes

public class ChainLoadActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        assert extras != null;
        ApplicationInfo launchApp = Objects.requireNonNull(extras.getParcelable("app"));

        // Get normal launch intent
        PackageManager pm = getPackageManager();
        final Intent normalIntent = pm.getLaunchIntentForPackage(launchApp.packageName);
        startActivity(normalIntent);
        finish();
    }
}
