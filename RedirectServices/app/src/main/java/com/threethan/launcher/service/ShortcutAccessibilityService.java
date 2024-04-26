package com.threethan.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Timer;
import java.util.TimerTask;

public class ShortcutAccessibilityService extends AccessibilityService {
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String eventText = event.getText().toString().toLowerCase();
            String targetName = getResources().getString(R.string.target_name);
            String targetEventName = "["+targetName+"]";

            if (targetEventName.toLowerCase().compareTo(eventText.toLowerCase()) == 0) {
                Intent launchIntent = new Intent(this, MainActivity.class);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

                Log.i("LightningLauncherService", "Opening launcher activity from accessibility event");
                startActivity(launchIntent);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        startActivity(launchIntent);
                    }
                }, 650);
            }
        }
    }

    public void onInterrupt() {}
}