package com.threethan.launcher.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    public static String packageToOpen;

    private void launch() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageToOpen);
        if (launchIntent != null) launchIt(launchIntent);
    }
    private void launchIt(Intent launchIntent) {
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        startActivity(launchIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launch();
    }
}
