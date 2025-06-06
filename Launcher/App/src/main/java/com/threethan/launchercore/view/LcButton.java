package com.threethan.launchercore.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.threethan.launcher.R;

public class LcButton extends Button {
    public LcButton(Context context) {
        super(context);
        init(null);
    }

    public LcButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public LcButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    protected void init(AttributeSet attrs) {
        LcToolTipHelper.init(this, attrs);
        if (attrs != null) {
            //noinspection resource
            TypedArray a = getContext().getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.LcButton,
                    0, 0);
            try {
                setAllCaps(false);
                int fontId = a.getResourceId(R.styleable.LcButton_font, R.font.sansserif);
                setTypeface(ResourcesCompat.getFont(getContext(), fontId));
                int gravity = a.getInt(R.styleable.LcButton_android_gravity, Gravity.CENTER);
                setGravity(gravity);
                int textSize = a.getDimensionPixelSize(R.styleable.LcButton_android_textSize, (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 20, getResources().getDisplayMetrics()));
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                int backgroundResource = a.getResourceId(R.styleable.LcButton_android_background, R.drawable.lc_bkg_button);
                setIncludeFontPadding(false);
                setBackgroundResource(backgroundResource);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void setTooltipText(@Nullable CharSequence tooltipText) {
        LcToolTipHelper.init(this, tooltipText);
    }
}
