package com.threethan.launcher.activity.view.status;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.threethan.launcher.activity.view.EditTextWatched;

public class StatusAdaptableEditTextWatched extends EditTextWatched implements StatusAdaptableView {
    public StatusAdaptableEditTextWatched(@NonNull Context context) {
        super(context);
    }

    public StatusAdaptableEditTextWatched(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusAdaptableEditTextWatched(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        setHintTextColor(darkMode ? 0x88FFFFFF : 0x88000000);
    }
}
