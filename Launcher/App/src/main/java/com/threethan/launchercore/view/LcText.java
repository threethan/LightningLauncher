package com.threethan.launchercore.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LcText extends TextView {
    public LcText(@NonNull Context context) {
        super(context);
    }

    public LcText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LcToolTipHelper.init(this, attrs);
    }

    public LcText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LcToolTipHelper.init(this, attrs);
    }
    @Override
    public void setTooltipText(@Nullable CharSequence tooltipText) {
        LcToolTipHelper.init(this, tooltipText);
    }
}
