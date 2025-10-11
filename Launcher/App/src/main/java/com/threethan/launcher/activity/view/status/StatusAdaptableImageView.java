package com.threethan.launcher.activity.view.status;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.Nullable;

public class StatusAdaptableImageView extends ImageView implements StatusAdaptableView {
    public StatusAdaptableImageView(Context context) {
        super(context);
    }

    public StatusAdaptableImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusAdaptableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** @noinspection unused*/
    public StatusAdaptableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        setColorFilter(darkMode ? Color.WHITE : Color.BLACK);
    }
}
