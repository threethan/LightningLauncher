package com.threethan.launcher.activity.view.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.BatteryManager;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import com.threethan.launchercore.util.Platform;


public class BatteryLevelView extends View implements StatusAdaptableView {
    String pathData = "M18.75,12.07l-7,-4c-0.16,-0.09 -0.35,-0.09 -0.5," +
            "0 -0.15,0.09 -0.25,0.25 -0.25,0.43v2.5h-5.5c-0.23,0 -0.42,0.15" +
            " -0.48,0.37 -0.06,0.22 0.04,0.45 0.24,0.56l7,4c0.08,0.04 0.16,0.07" +
            " 0.25,0.07s0.17,-0.02 0.25,-0.07c0.15,-0.09 0.25,-0.25 0.25,-0.43v-2.5h5.5c0.23," +
            "0 0.42,-0.15 0.48,-0.37 0.06,-0.22 -0.04,-0.45 -0.24,-0.56Z";
    Path path = PathParser.createPathFromPathData(pathData);
    private int batteryLevel = 100;
    private boolean charging = false;
    private boolean darkMode = true;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra("level", 0);
            int scale = intent.getIntExtra("scale", 100);
            setBatteryLevel((int) ((level / (float) scale) * 100));
        }
    };
    private final BroadcastReceiver chargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            charging = true;
            invalidate();
        }
    };
    private final BroadcastReceiver dischargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            charging = false;
            invalidate();
        }
    };

    public BatteryLevelView(Context context) {
        super(context);
        init(context);

        // Check if device has a battery. Hide if not.
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) < 0) setVisibility(GONE);
    }

    public BatteryLevelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        BatteryManager bm;
        try {
            bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        } catch (Exception e) {
            // No battery service, hide view
            setVisibility(GONE);
            return;
        }
        // check for tv or unknown battery status. hide if no battery present.
        if (Platform.isTv() ||
                (bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_UNKNOWN
                && bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 0)
        || bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) < 0) {
            setVisibility(GONE);
            return;
        }

        int color = darkMode ? Color.WHITE : Color.BLACK;

        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(dp(1.5));
        strokePaint.setStyle(Paint.Style.STROKE);

        mainPaint.setColor(color);
        mainPaint.setStyle(Paint.Style.FILL);
        mainPaint.setTextAlign(Paint.Align.CENTER);
        mainPaint.setFakeBoldText(true);
        mainPaint.setTextSize(dp(10));

        charging = bm.isCharging();

        clearStrokePaint.setStyle(Paint.Style.STROKE);
        clearStrokePaint.setTextAlign(Paint.Align.CENTER);
        clearStrokePaint.setTextSize(dp(10));
        clearStrokePaint.setFakeBoldText(true);

        clearStrokePaint.setStrokeWidth(dp(3));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clearStrokePaint.setBlendMode(BlendMode.CLEAR);
            clearStrokePaint.setColor(Color.TRANSPARENT);
        } else {
            clearStrokePaint.setColor(Color.GRAY);
        }

        invalidate();

        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            charging = chargePlug == BatteryManager.BATTERY_PLUGGED_AC || chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        }
    }

    private float dp(double px) {
        return (float) (px * getResources().getDisplayMetrics().density);
    }

    public void setBatteryLevel(int level) {
        batteryLevel = Math.max(0, Math.min(100, level));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Register for battery change updates
        getContext().registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        getContext().registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        getContext().registerReceiver(dischargingReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            getContext().unregisterReceiver(batteryReceiver);
            getContext().unregisterReceiver(chargingReceiver);
            getContext().unregisterReceiver(dischargingReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float o = dp(5);
        float or = dp(3);
        float ir = dp(1);
        float i = dp(2) + o;

        // use separate layer
        int layerId = canvas.saveLayer(0, 0, width, height, null);



        float progWidth = batteryLevel / 100f * (width-i*2) + i;
        canvas.drawRoundRect(i, i, progWidth, height - i, ir, ir, mainPaint);

        if (charging) {
            // Bolt
            float cx = (width-25f)/2 + dp(0.6);
            float cy = (height - 24f)/2;
            path.offset(cx, cy);
            canvas.drawPath(path, clearStrokePaint);
            canvas.drawPath(path, mainPaint);
            path.offset(-cx, -cy);
        } else {
            // Percent text
            String text = batteryLevel + "";
            float x = width / 2f;
            float y = height / 2f - ((clearStrokePaint.descent() + clearStrokePaint.ascent()) / 1.99f);
            canvas.drawText(text, x, y, clearStrokePaint);
            canvas.drawText(text, x, y, mainPaint);
        }

        canvas.restoreToCount(layerId);

        // Draw bg
        canvas.drawRoundRect(o, o, width-o, height-o, or, or, strokePaint);
        // Nubbin
        canvas.drawRoundRect(width - o, height*0.38f, width - o + dp(2.25), height*0.62f, or, or, mainPaint);

    }

    @Override
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        init(getContext());
        invalidate();
    }
}
