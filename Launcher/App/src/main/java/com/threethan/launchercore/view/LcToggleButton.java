package com.threethan.launchercore.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;

public class LcToggleButton extends ToggleButton {
    public LcToggleButton(Context context) {
        super(context);
        setStateListAnimator(null);
    }

    public LcToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        LcToolTipHelper.init(this, attrs);
        setStateListAnimator(null);
    }

    public LcToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LcToolTipHelper.init(this, attrs);
        setStateListAnimator(null);
    }

    @Override
    public void setTooltipText(@Nullable CharSequence tooltipText) {
        LcToolTipHelper.init(this, tooltipText);
    }
}
