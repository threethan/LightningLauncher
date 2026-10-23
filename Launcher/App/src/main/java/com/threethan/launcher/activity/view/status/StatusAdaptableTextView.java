package com.threethan.launcher.activity.view.status;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

/** A text view in the top bar that follows the launcher's light/dark mode */
@SuppressLint("AppCompatCustomView")
public class StatusAdaptableTextView extends TextView implements StatusAdaptableView {
    public StatusAdaptableTextView(Context context) {
        super(context);
    }

    public StatusAdaptableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusAdaptableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        setTextColor(darkMode ? Color.WHITE : Color.BLACK);
    }
}
