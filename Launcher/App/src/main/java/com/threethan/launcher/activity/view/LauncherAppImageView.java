package com.threethan.launcher.activity.view;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.Core;
import com.threethan.launchercore.util.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A version of ImageView specifically for showing app icons.
 * Intended to be slightly lighter weight than a full ImageView.
 * Caches a bitmap of the drawable at the correct size to avoid re-scaling on every,
 * and implements parallax w/ translationX/Y/Z.
 * Also inc*/
public class LauncherAppImageView extends ImageView {
    private Drawable drawable;

    public LauncherAppImageView(Context context) {
        super(context);
    }

    public LauncherAppImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LauncherAppImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** @noinspection unused*/
    public LauncherAppImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        this.drawable = drawable;
        cacheBitmap.set(null);
        updateAsyncCache();
    }

    private final AtomicBoolean awaitingBoundsUpdate = new AtomicBoolean(false);

    private synchronized void updateAsyncCache() {
        if (drawable == null || awaitingBoundsUpdate.getAndSet(true)) return;
        Core.EXECUTOR.submit(() -> {
            if (drawable != null) {
                if (cacheBitmap.get() != null) {
                    cacheBitmap.get().recycle();
                    cacheBitmap.set(null);
                }
                int viewWidth = getMeasuredWidth();
                int viewHeight = getMeasuredHeight();
                int drawableWidth = drawable.getIntrinsicWidth();
                int drawableHeight = drawable.getIntrinsicHeight();

                float scale = Math.max(
                        (float) viewWidth / (float) drawableWidth,
                        (float) viewHeight / (float) drawableHeight
                );

                int scaledWidth = Math.round(drawableWidth * scale);
                int scaledHeight = Math.round(drawableHeight * scale);

                int left = (viewWidth - scaledWidth) / 2;
                int top = (viewHeight - scaledHeight) / 2;
                int right = left + scaledWidth;
                int bottom = top + scaledHeight;

                drawable.setBounds(left, top, right, bottom);

                // Draw drawable to a bitmap
                Bitmap bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                Canvas bitmapCanvas = new Canvas(bitmap);
                boolean drawPhoneMaskBg = isPhone && viewWidth - viewHeight < 5 && getBackgroundForPhone() != null;
                if (drawPhoneMaskBg) {
                    backgroundForPhone.setBounds(0, 0, viewWidth, viewHeight);
                    backgroundForPhone.draw(bitmapCanvas);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && drawPhoneMaskBg) {
                    bitmapCanvas.saveLayer(0, 0, viewWidth, viewHeight, bitmapPaint);
                    drawable.draw(bitmapCanvas);
                    bitmapCanvas.restore();
                } else {
                    drawable.draw(bitmapCanvas);
                }
                cacheBitmap.set(bitmap);

                post(() -> {
                    awaitingBoundsUpdate.set(false);
                    invalidate();
                });
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPhone) {
            setBackground(getBackgroundForPhone());
        }
    }

    private boolean hasMeasured = false;
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isPhone && !hasMeasured) {
            hasMeasured = true;
            if (getMeasuredWidth() - getMeasuredHeight() > 5) {
                setBackground(getContext().getDrawable(R.drawable.bkg_app));
            }
        }
    }

    private Drawable backgroundForPhone = null;
    private boolean noBackgroundForPhone = false;
    private synchronized Drawable getBackgroundForPhone() {
        if (noBackgroundForPhone) return null;
        if (backgroundForPhone != null) return backgroundForPhone;
        PackageManager pm = getContext().getPackageManager();
        Drawable someIcon = null;
        for (String testPkg : new String[] {
                "com.android.settings",
                "com.google.chrome",
                "com.threethan.launcher",
                "com.threethan.launcher.playstore"}) {
            try {
                someIcon = pm.getApplicationIcon(testPkg);
                break;
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore
            }
        }
        if (someIcon != null) {
            Bitmap someIconBitmap = Bitmap.createBitmap(someIcon.getIntrinsicWidth(), someIcon.getIntrinsicHeight(), Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas(someIconBitmap);
            someIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            someIcon.draw(canvas);
            backgroundForPhone = new BitmapDrawable(getResources(), someIconBitmap);
        } else {
            noBackgroundForPhone = true;
        }
        return backgroundForPhone;
    }
    private static boolean isPhone = Platform.isPhone();

    private static final Paint bitmapPaint = new Paint();
    static {
        bitmapPaint.setFilterBitmap(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isPhone) {
            bitmapPaint.setBlendMode(BlendMode.SRC_ATOP);
        }
    }
    private final AtomicReference<Bitmap> cacheBitmap = new AtomicReference<>( null );
    @Override
    public void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (drawable != null) {
            if (cacheBitmap.get() == null) {
                // Re-apply bounds if needed
                updateAsyncCache();
            } else if (!awaitingBoundsUpdate.get() && cacheBitmap.get() != null) {
                canvas.drawBitmap(cacheBitmap.get(), 0, 0, bitmapPaint);

                if (translationZ <= 0.001f && translationZ >= -0.001f || translationParent == null || isPhone) {
                    return;
                }
                float translationX = translationParent.getRotationY() * -40f;
                float translationY = -translationParent.getRotationX() * -40f;

                // apply parallax
                canvas.save();
                float scale = (float) (1+(Math.sqrt(Math.abs(translationZ)) * getMeasuredWidth() / 15000f));
                canvas.scale(scale, scale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
                float transM = getWidth() * translationZ / 15000f;
                canvas.drawBitmap(cacheBitmap.get(), translationX*transM, translationY*transM, bitmapPaint);
                canvas.restore();
            }
        }
    }

    // TranslationZ is used as a sort of "depth" factor for parallax
    private float translationZ = 0;
    public void setTranslationZ(float translationZ) {
        this.translationZ = translationZ;
        invalidate();
    }

    @Override
    public float getTranslationZ() {
        return translationZ;
    }

    private View translationParent = null;
    public void setTranslationParent(View translationParent) {
        this.translationParent = translationParent;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (
                changed && (cacheBitmap.get() == null
                || Math.abs(cacheBitmap.get().getWidth() - (right-left)) > 1)
        ) cacheBitmap.set(null);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public Drawable getDrawable() {
        return drawable;
    }
}
