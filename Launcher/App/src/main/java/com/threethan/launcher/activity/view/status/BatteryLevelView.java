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
        int color = darkMode ? Color.WHITE : Color.BLACK;

        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(1.5f);
        strokePaint.setStyle(Paint.Style.STROKE);

        mainPaint.setColor(color);
        mainPaint.setStyle(Paint.Style.FILL);
        mainPaint.setTextAlign(Paint.Align.CENTER);
        mainPaint.setFakeBoldText(true);
        mainPaint.setTextSize(16);

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        charging = bm.isCharging();

        clearStrokePaint.setStyle(Paint.Style.STROKE);
        clearStrokePaint.setTextAlign(Paint.Align.CENTER);
        clearStrokePaint.setTextSize(16);
        clearStrokePaint.setFakeBoldText(true);

        clearStrokePaint.setStrokeWidth(3);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clearStrokePaint.setBlendMode(BlendMode.CLEAR);
            clearStrokePaint.setColor(Color.TRANSPARENT);
        } else {
            clearStrokePaint.setColor(Color.GRAY);
        }

        // Register for battery change updates
        context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        context.registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        context.registerReceiver(dischargingReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));

        invalidate();

        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            charging = chargePlug == BatteryManager.BATTERY_PLUGGED_AC || chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        }
    }

    public void setBatteryLevel(int level) {
        batteryLevel = Math.max(0, Math.min(100, level));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            getContext().unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int o = 5;
        float or = 4;
        float ir = 2;
        int i = 2 + o;

        // use separate layer
        int layerId = canvas.saveLayer(0, 0, width, height, null);

        // Draw bg
        canvas.drawRoundRect(o, o, width-o, height-o, or, or, strokePaint);
        // Nubbin
        canvas.drawRoundRect(width - o + 2, height*0.4f, width - o + 4, height*0.6f, or, or, mainPaint);


        float progWidth = batteryLevel / 100f * (width-i*2) + i;
        canvas.drawRoundRect(i, i, progWidth, height - i, ir, ir, mainPaint);

        if (charging) {
            // Bolt
            float cx = (width-25f)/2;
            float cy = (height - 24f)/2;
            path.offset(cx, cy);
            canvas.drawPath(path, clearStrokePaint);
            canvas.drawPath(path, mainPaint);
            path.offset(-cx, -cy);
        } else {
            // Percent text
            String text = batteryLevel + "";
            float x = width / 2f - 1;
            float y = height / 2f - ((clearStrokePaint.descent() + clearStrokePaint.ascent()) / 2f);
            canvas.drawText(text, x, y, clearStrokePaint);
            canvas.drawText(text, x, y, mainPaint);
        }

        canvas.restoreToCount(layerId);
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        init(getContext());
        invalidate();
    }
}
