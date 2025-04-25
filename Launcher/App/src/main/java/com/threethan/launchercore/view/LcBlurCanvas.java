package com.threethan.launchercore.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

public class LcBlurCanvas extends LcContainerView {
    protected static final RenderNode renderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new RenderNode("BackgroundNode") : null;
    protected static @Nullable Bitmap fallbackBitmap = null;
    protected static final float BLUR_RADIUS = 25f;

    /** Legacy blur (API < Q) is much less performant, so it is rendered at resolution / this */
    protected static final int LEGACY_DOWN_SAMPLE = 32;

    public static RenderNode getRenderNode() {
        return renderNode;
    }

    @Nullable
    public static Bitmap getFallbackBitmap() {
        return fallbackBitmap;
    }

    /** Drawn on top of the canvas */
    private static int overlayColor = Color.TRANSPARENT;

    /** Sets a color to be drawn on top of the canvas */
    public static void setOverlayColor(int overlayColor) {
        LcBlurCanvas.overlayColor = overlayColor;
    }


    /** Renders the canvas as-needed */
    private final ViewTreeObserver.OnPreDrawListener listener = () -> {
        try {
            if (getChildCount() == 0) return true;
            // Get window dimensions
            int height;
            int width;
            try {
                height = ((Activity) getContext()).getWindow().getDecorView().getHeight();
                width = ((Activity) getContext()).getWindow().getDecorView().getWidth();
            } catch (Exception e) {
                height = getChildAt(0).getHeight();
                width = getChildAt(0).getWidth();
            }
            if (width == 0 || height == 0) {
                width = 1280;
                height = 720;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                renderNode.setPosition(0, 0, width, height);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Canvas canvas = renderNode.beginRecording();

                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    // Draw window background
                    drawWindowBackground(canvas);
                    drawChild(canvas);

                    // Canvas overlay
                    canvas.drawColor(overlayColor);

                    renderNode.endRecording();

                    renderNode.setRenderEffect(RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP));

                } else {
                    renderLegacyBlur(renderNode.beginRecording(), width, height);
                    renderNode.endRecording();
                }
            } else {
                fallbackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                renderLegacyBlur(new Canvas(fallbackBitmap), width, height);
            }
        } catch (Exception e) {
            Log.w("LcBlurCanvas", "Error while drawing", e);
        }

        return true;
    };

    private void drawChild(Canvas canvas) {
        int[] location = new int[2];
        getLocationInWindow(location);
        canvas.translate(location[0], location[1]);
        // Draw child
        getChildAt(0).draw(canvas);
        canvas.translate(-location[0], -location[1]);
    }

    private void renderLegacyBlur(Canvas canvas, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width / LEGACY_DOWN_SAMPLE, height / LEGACY_DOWN_SAMPLE, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        bitmapCanvas.scale(1f / LEGACY_DOWN_SAMPLE, 1f / LEGACY_DOWN_SAMPLE);
        // Draw window background
        drawWindowBackground(bitmapCanvas);
        drawChild(bitmapCanvas);

        // Blur bitmap
        blurBitmap(bitmap, (float) Math.ceil(BLUR_RADIUS / LEGACY_DOWN_SAMPLE));

        canvas.scale(LEGACY_DOWN_SAMPLE, LEGACY_DOWN_SAMPLE);
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.drawColor(overlayColor);
    }

    /**
     * @noinspection deprecation, SameParameterValue
     */
    private void blurBitmap(Bitmap bitmap, float radius) {
        RenderScript rs = RenderScript.create(getContext());
        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation in = Allocation.createFromBitmap(rs, bitmap);
        Allocation out = Allocation.createFromBitmap(rs, bitmap);
        blur.setInput(in);
        blur.setRadius(Math.min(Math.max(radius, 0.1f), 25));
        blur.forEach(out);
        out.copyTo(bitmap);
    }

    private void drawWindowBackground(Canvas canvas) {
        if (getContext() instanceof Activity activity) {
            try {
                Drawable windowBackground = activity.getWindow().getDecorView().getBackground();
                if (windowBackground != null) {
                    windowBackground.draw(canvas);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public LcBlurCanvas(Context context) {
        super(context);
    }

    public LcBlurCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LcBlurCanvas(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        super.addView(child, index, params);
        child.getViewTreeObserver().addOnPreDrawListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (getChildCount() > 0)
            getChildAt(0).getViewTreeObserver().removeOnPreDrawListener(listener);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getChildCount() > 0)
            getChildAt(0).getViewTreeObserver().addOnPreDrawListener(listener);
    }
}
