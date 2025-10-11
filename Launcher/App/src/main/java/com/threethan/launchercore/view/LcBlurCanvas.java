package com.threethan.launchercore.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
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

import java.util.HashSet;
import java.util.Set;

public class LcBlurCanvas extends LcContainerView {
    protected static final RenderNode renderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new RenderNode("BackgroundNode") : null;
    protected static @Nullable Bitmap fallbackBitmap = null;
    protected static final float BLUR_RADIUS = 25f;
    protected static final Set<LcBlurCanvas> instances = new HashSet<>();

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

    /** Whether the renderRect should be used to limit the rendering area */
    public static boolean useRenderRect = false;

    /** The region of the canvas that should be rendered. Used when useRenderRect is true */
    public static Rect renderRect = new Rect();

    /** Sets a color to be drawn on top of the canvas */
    public static void setOverlayColor(int overlayColor) {
        LcBlurCanvas.overlayColor = overlayColor;
    }

    /** Invalidates all existing LcBlurCanvas instances, causing them to redraw */
    public static void invalidateAll() {
        instances.forEach(View::postInvalidate);
    }

    private boolean hasRenderEffect;
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
                height = getChildAt(0).getHeight() + 100;
                width = getChildAt(0).getWidth();
            }
            if (width == 0 || height == 0) {
                width = 1280;
                height = 720;
            }

            if (useRenderRect && renderRect == null) return true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (useRenderRect)
                    renderNode.setPosition(renderRect);
                else
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

                    if (!hasRenderEffect) {
                        renderNode.setRenderEffect(RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP));
                        hasRenderEffect = true;
                    }

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
        final int bitmapWidth = (int) Math.ceil((double) width / LEGACY_DOWN_SAMPLE);
        final int bitmapHeight = (int) Math.ceil((double) height / LEGACY_DOWN_SAMPLE);
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
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
        instances.remove(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getChildCount() > 0)
            getChildAt(0).getViewTreeObserver().addOnPreDrawListener(listener);
        instances.add(this);
    }
}
