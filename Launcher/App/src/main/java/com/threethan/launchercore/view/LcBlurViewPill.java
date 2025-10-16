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

    private static final float MARGIN = 0.5f; // To avoid edge artifacts

    @Override
    protected void clearCanvas(Canvas canvas) {
        canvas.drawRoundRect(MARGIN, MARGIN, getWidth()-MARGIN, getHeight()-MARGIN, getHeight()/2f, getHeight()/2f, clearPaint);
    }

    @Override
    protected void drawBlur(Canvas canvas, int[] position) {
        canvas.translate(0, Y_OFFSET);
        super.drawBlur(canvas, position);
        canvas.translate(0, -Y_OFFSET);
    }
}
