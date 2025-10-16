package com.threethan.launcher.activity.view.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.threethan.launcher.R;
import com.threethan.launchercore.util.Platform;

public class WifiStatusIndicator extends View implements StatusAdaptableView {

    private int signalLevel = 0; // 0–3
    private boolean noWifi = false;

    private Drawable[] signalDrawables;
    private Drawable signalOffDrawable;
    private boolean darkMode = true;

    public WifiStatusIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // On TVs, it's reasonable to expect a good connection is always present
        if (Platform.isTv()) {
            setVisibility(GONE);
            return;
        }

        signalDrawables = new Drawable[]{
                ContextCompat.getDrawable(context, R.drawable.status_wifi_signal_0),
                ContextCompat.getDrawable(context, R.drawable.status_wifi_signal_1),
                ContextCompat.getDrawable(context, R.drawable.status_wifi_signal_2),
                ContextCompat.getDrawable(context, R.drawable.status_wifi_signal_3)
        };

        getContext().registerReceiver(receiverRSSI, filterRSSI);
        getContext().registerReceiver(receiverEnablement, filterEnabled);

        signalOffDrawable = ContextCompat.getDrawable(context, R.drawable.status_wifi_signal_off);
        updateState(context);

        noWifi = !isWifiEnabled(context);

    }


    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (noWifi && signalOffDrawable != null) {
            signalOffDrawable.setBounds(0, 0, getWidth(), getHeight());
            signalOffDrawable.setColorFilter(darkMode ? Color.WHITE : Color.BLACK, PorterDuff.Mode.SRC_ATOP);
            signalOffDrawable.draw(canvas);
        } else {
            Drawable signalDrawable = signalDrawables[signalLevel];
            if (signalDrawable != null) {
                signalDrawable.setBounds(0, 0, getWidth(), getHeight());
                signalDrawable.setColorFilter(darkMode ? Color.WHITE : Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                signalDrawable.draw(canvas);
            }
        }
    }

    IntentFilter filterRSSI    = new IntentFilter(WifiManager.RSSI_CHANGED_ACTION);
    IntentFilter filterEnabled = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    private final BroadcastReceiver receiverRSSI = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState(context);
        }
    };
    private final BroadcastReceiver receiverEnablement = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

                switch (wifiState) {
                    case WifiManager.WIFI_STATE_ENABLED:
                        // Wi-Fi has been turned on
                        noWifi = false;
                        invalidate();
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        // Wi-Fi has been turned off
                        noWifi = true;
                        invalidate();
                        break;
                }
            }
        }
    };

    private void updateState(Context context) {
        signalLevel = Math.max(0, Math.min(3, getSignalLevel(context)));
        invalidate();
    }

    private static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }
    private static int getSignalLevel(Context context) {
        if (!isConnectedToInternet(context)) return 0;

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null || wifiInfo.getNetworkId() == -1) return 0;

        int rssi = wifiInfo.getRssi(); // usually -100 to 0
        // Normalize to 1–3
        return 1+ WifiManager.calculateSignalLevel(rssi, 3);
    }

    private static boolean isConnectedToInternet(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo != null && wifiInfo.getNetworkId() != -1;
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        invalidate();
    }
}
