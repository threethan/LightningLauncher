package com.threethan.launchercore.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;

/**
 * FrameLayout that blurs its underlying content.
 * Can have children and draw them over blurred background.
 */
public class LcBlurViewPill extends LcBlurView {
    /** Adds an offset for the blur for an interesting visual effect */
    private static final float Y_OFFSET = -4f;

    public LcBlurViewPill(Context context) {
        super(context);
    }

    public LcBlurViewPill(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LcBlurViewPill(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private static final Paint clearPaint = new Paint();
    static {
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);
        clearPaint.setColor(0x0);
    }

    private static final float MARGIN = 0.5f; // About artifacts. Side effect of slight outline

    @Override
    protected void clearCanvas(Canvas canvas) {

        canvas.drawRoundRect(MARGIN, MARGIN, getWidth() - MARGIN, getHeight() - MARGIN,
                100f, 100f, clearPaint);
    }



    @Override
    protected void drawBlur(Canvas canvas, int[] position) {
        canvas.translate(0, Y_OFFSET * LcBlurCanvas.MODERN_RES_MULT);
        super.drawBlur(canvas, position);
        canvas.translate(0, -Y_OFFSET * LcBlurCanvas.MODERN_RES_MULT);
    }
}
