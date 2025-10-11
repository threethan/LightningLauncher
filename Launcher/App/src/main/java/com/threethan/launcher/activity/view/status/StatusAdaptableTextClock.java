package com.threethan.launcher.activity.view.status;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextClock;

public class StatusAdaptableTextClock extends TextClock implements StatusAdaptableView {
    public StatusAdaptableTextClock(Context context) {
        super(context);
    }

    public StatusAdaptableTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusAdaptableTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setDarkMode(boolean darkMode) {
        setTextColor(darkMode ? Color.WHITE : Color.BLACK);
    }
}
