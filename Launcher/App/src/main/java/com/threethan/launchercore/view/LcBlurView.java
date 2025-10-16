package com.threethan.launchercore.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * FrameLayout that blurs its underlying content.
 * Can have children and draw them over blurred background.
 */
public class LcBlurView extends FrameLayout {
    public LcBlurView(Context context) {
        super(context);
    }

    public LcBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LcBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // Prevents allocation during onDraw
    private final int[] mPosition = new int[2];
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        getLocationInWindow(mPosition);
        clearCanvas(canvas);
        drawBlur(canvas, mPosition);
        super.onDraw(canvas);
    }

    protected void clearCanvas(Canvas canvas) {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
    }

    protected void drawBlur(Canvas canvas, int[] position) {
        // Draw blurred content
        canvas.translate(-position[0], -position[1]);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            canvas.scale(1f / LcBlurCanvas.MODERN_RES_MULT, 1f / LcBlurCanvas.MODERN_RES_MULT);
            canvas.drawRenderNode(LcBlurCanvas.getRenderNode());
            canvas.scale(LcBlurCanvas.MODERN_RES_MULT, LcBlurCanvas.MODERN_RES_MULT);
        } else {
            Bitmap bitmap = LcBlurCanvas.getFallbackBitmap();
            if (bitmap != null) canvas.drawBitmap(bitmap, 0, 0, null);
            LcBlurCanvas.addInvalidatingView(this);
        }
        canvas.translate(position[0], position[1]);
    }
}
