package com.threethan.launcher.activity.support;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.threethan.launcher.activity.LauncherActivity;
import com.threethan.launcher.data.Settings;
import com.threethan.launcher.helper.PlatformExt;
import com.threethan.launchercore.lib.ImageLib;
import com.threethan.launchercore.util.Platform;
import com.threethan.launchercore.view.LcBlurCanvas;

import org.intellij.lang.annotations.Language;

import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
    Loads an image background asynchronously, then sets it to the specified view when done
    It is also responsible from cropping/resizing the image for different window sizes
    (Used only by LauncherActivity)
 */

public class WallpaperLoader {

    private Bitmap baseBitmap = null;
    private Bitmap imageBitmap = null;
    final LauncherActivity owner;
    private final Object lock = new Object();
    public WallpaperLoader(LauncherActivity owner) {
        this.owner = owner;
    }
    public void crop() {
        Thread thread = new Thread(this::cropInternalAndApply);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
    public void load() {
        Thread thread = new Thread(() -> {
            loadInternal();
            cropInternalAndApply();
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void cropInternalAndApply() {
        if (imageBitmap == null) loadInternal();
        synchronized (lock) {
            if (imageBitmap == null || baseBitmap == null) return;

            imageBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
            float aspectScreen = getAspectScreen(owner);

            float aspectImage = imageBitmap.getWidth() / (float) imageBitmap.getHeight();
            int cropWidth = imageBitmap.getWidth();
            int cropHeight = imageBitmap.getHeight();

            if (aspectScreen < aspectImage)
                cropWidth = (int) (imageBitmap.getHeight() * aspectScreen);
            else cropHeight = (int) (imageBitmap.getWidth() / aspectScreen);

            int cropMarginWidth = imageBitmap.getWidth() - cropWidth;
            int cropMarginHeight = imageBitmap.getHeight() - cropHeight;

            imageBitmap = Bitmap.createBitmap(imageBitmap, cropMarginWidth, cropMarginHeight,
                    imageBitmap.getWidth() - cropMarginWidth,
                    imageBitmap.getHeight() - cropMarginHeight);

            BitmapDrawable wallpaperDrawable = new BitmapDrawable(Resources.getSystem(), imageBitmap);

            int backgroundAlpha = getBackgroundAlpha(owner.getDataStoreEditor());
            if (Platform.isQuest() && backgroundAlpha < 254) {
                LcBlurCanvas.setUseTranslucent(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && owner.getDataStoreEditor().getBoolean(Settings.KEY_BACKGROUND_ALPHA_PRESERVE,
                        Settings.DEFAULT_BACKGROUND_ALPHA_PRESERVE)) {
                    float alpha = backgroundAlpha / 255f;
                    imageBitmap = preserveAlphaBitmap(imageBitmap, alpha, shouldClampAlpha(owner.getDataStoreEditor()));
                    wallpaperDrawable = new BitmapDrawable(Resources.getSystem(), imageBitmap);
                } else {
                    wallpaperDrawable.setAlpha(backgroundAlpha + 1);
                }
            } else {
                LcBlurCanvas.setUseTranslucent(false);
            }
            // Apply
            BitmapDrawable finalWallpaperDrawable = wallpaperDrawable;
            owner.runOnUiThread(() -> {
                    owner.getWindow().setBackgroundDrawable(finalWallpaperDrawable);
                    owner.getWindow().getDecorView().invalidate();
                    LcBlurCanvas.invalidateAll();
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @NonNull
    private static RuntimeShader getPreserveAlphaRuntimeShader() {
        @Language("AGSL") String shaderCode = """
            uniform shader content;
            uniform float fade;
            uniform float maxAlpha;
            uniform float minAlpha;
        
            half4 main(float2 coord) {
                half4 color = content.eval(coord);
                float len = length(color.rgb) / 1.25;
                float a = fade + (1.0 - fade) * len;
                float b = ((1. - a) / 3) + 1. + (1. - clamp(maxAlpha, 0., 1.))/3;
                a = clamp(a, minAlpha, maxAlpha);
                color.r *= b;
                color.g *= b;
                color.b *= b;
                color.a *= a;
                return color;
            }
        """;

        return new RuntimeShader(shaderCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private static Bitmap preserveAlphaBitmap(Bitmap imageBitmap, float alpha, boolean shouldClampAlpha) {

        RuntimeShader runtimeShader = getPreserveAlphaRuntimeShader();
        runtimeShader.setFloatUniform("fade",(alpha*0.6f-0.3f)*3.34f);
        runtimeShader.setFloatUniform("maxAlpha", alpha*0.5f+0.5f);
        runtimeShader.setFloatUniform("minAlpha", shouldClampAlpha ? 0.8425f : 0.0f);

        // Set up RenderNode to render to a HardwareBuffer
        RenderNode renderNode = new RenderNode("shaderNode");
        renderNode.setPosition(0, 0, imageBitmap.getWidth(), imageBitmap.getHeight());
        renderNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(runtimeShader, "content"));

        Bitmap output = Bitmap.createBitmap(imageBitmap.getWidth(), imageBitmap.getHeight(), Bitmap.Config.ARGB_8888);

        // Use a recording canvas to draw the shader effect
        RecordingCanvas canvas = renderNode.beginRecording();
        Paint paint = new Paint();
        paint.setShader(new BitmapShader(imageBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, imageBitmap.getWidth(), imageBitmap.getHeight(), paint);
        renderNode.endRecording();

        // Create surface
        SurfaceTexture surfaceTexture = new SurfaceTexture(0);
        surfaceTexture.setDefaultBufferSize(imageBitmap.getWidth(), imageBitmap.getHeight());
        Surface surface = new Surface(surfaceTexture);

        // Render to surface
        HardwareRenderer renderer = new HardwareRenderer();
        renderer.setContentRoot(renderNode);
        renderer.setSurface(surface);
        renderer.createRenderRequest().syncAndDraw();

        // Copy to bitmap
        CountDownLatch latch = new CountDownLatch(1);
        PixelCopy.request(surface, output, copyResult -> latch.countDown(), new Handler(Looper.getMainLooper()));
        try {
            latch.await();
        } catch (InterruptedException ignored) {}

        // Cleanup
        renderer.destroy();
        surface.release();
        surfaceTexture.release();

        return output;
    }

    public void loadInternal() {
        synchronized (lock) {
            int background = LauncherActivity.backgroundIndex;
            imageBitmap = null;
            if (background >= 0 && background < SettingsManager.BACKGROUND_DRAWABLES.length) {
                // Create a cropped image asset for the window background
                imageBitmap = BitmapFactory.decodeResource(owner.getResources(), SettingsManager.BACKGROUND_DRAWABLES[background]);
                baseBitmap = imageBitmap;
            } else {
                File file = new File(owner.getApplicationInfo().dataDir, Settings.CUSTOM_BACKGROUND_PATH);
                try {
                    imageBitmap = ImageLib.bitmapFromFile(file);
                    baseBitmap = imageBitmap;
                } catch (Exception ignored) {
                } // In case file no longer exists or similar
            }
        }
    }

    private static float getAspectScreen(Activity owner) {
        float aspectScreen;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = owner.getWindowManager().getCurrentWindowMetrics().getBounds();
            aspectScreen = bounds.width() / (float) bounds.height();
        } else {
            int heightPixels = Resources.getSystem().getDisplayMetrics().heightPixels;
            int widthPixels = Resources.getSystem().getDisplayMetrics().widthPixels;
            aspectScreen = widthPixels / (float) heightPixels;
        }
        return aspectScreen;
    }

    /** Quest only. Clamped between 1 and 254 to prevent compositing issues. */
    public static int getBackgroundAlpha(DataStoreEditor dataStoreEditor) {
        int alpha = dataStoreEditor.getInt(Settings.KEY_BACKGROUND_ALPHA, Settings.DEFAULT_ALPHA);
        if (shouldClampAlpha(dataStoreEditor))
            return 215 + (int) Math.max(0, Math.min(alpha, 255)/6.375f);
        return Math.max(1, Math.min(alpha, 254));
    }
    private static boolean shouldClampAlpha(DataStoreEditor dataStoreEditor) {
        return PlatformExt.doesSupportBlendEffects()
                && dataStoreEditor.getBoolean(Settings.KEY_BACKGROUND_BLUR,
                                              Settings.DEFAULT_BACKGROUND_BLUR);
    }
}
