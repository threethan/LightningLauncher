package com.threethan.launcher.activity.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;

import com.threethan.launchercore.Core;
import com.threethan.launchercore.view.LcBlurView;

public class TopGradientBlurView extends LcBlurView {

    private static final int OVERLAY_COLOR = 0x20000000;

    public TopGradientBlurView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public TopGradientBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public TopGradientBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
    public static final int HEIGHT = dp(Core.context(), 80f); // Height of the view in pixels
    private static final float FADE_CENTER = 0.7f;
    private static final float FADE_START = 0.3f;

    private static final LinearGradient gradDn = new LinearGradient(
            0, 0, 0, HEIGHT,
            new int[] {0xFFFFFFFF, 0xF3FFFFFF, 0x00FFFFFF},
            new float[] {FADE_START, FADE_CENTER, 1f},
            Shader.TileMode.REPEAT
    );

    private static final Paint multiplyPaint = new Paint();
    static {
        multiplyPaint.setXfermode(new PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY));
        multiplyPaint.setAntiAlias(false);
        multiplyPaint.setFilterBitmap(false);
        multiplyPaint.setDither(false);
        multiplyPaint.setShader(gradDn);
    }
    private static final Paint normalPaint = new Paint();

    @Override
    protected void clearCanvas(Canvas canvas) {}

    @Override
    protected void drawBlur(Canvas canvas, int[] position) {
        int layerId = canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                normalPaint);

        canvas.translate(-position[0], -position[1]);

        super.drawBlur(canvas, position);
        canvas.drawColor(OVERLAY_COLOR);

        canvas.translate( position[0],  position[1]);

        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), multiplyPaint);

        canvas.restoreToCount(layerId);
    }
}
