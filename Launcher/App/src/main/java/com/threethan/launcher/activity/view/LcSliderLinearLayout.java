package com.threethan.launcher.activity.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.threethan.launchercore.view.LcToolTipHelper;

public class LcSliderLinearLayout extends LinearLayout {
    public LcSliderLinearLayout(Context context) {
        super(context);
    }

    public LcSliderLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LcToolTipHelper.init(this, attrs);
        init();
    }

    public LcSliderLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LcToolTipHelper.init(this, attrs);
        init();
    }

    /** @noinspection unused*/
    public LcSliderLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        LcToolTipHelper.init(this, attrs);
        init();
    }

    private void init() {
        post(() -> {
            if (getChildCount() == 3 && getChildAt(1) instanceof SeekBar seekBar) {
                int step = (seekBar.getMax() - seekBar.getMin()) / 10;
                getChildAt(0).setOnClickListener(v -> {
                    int progress = seekBar.getProgress() - step;
                    if (progress < seekBar.getMin()) progress = seekBar.getMin();
                    seekBar.setProgress(progress);
                });
                getChildAt(2).setOnClickListener(v -> {
                    int progress = seekBar.getProgress() + step;
                    if (progress > seekBar.getMax()) progress = seekBar.getMax();
                    seekBar.setProgress(progress);
                });
                getChildAt(0).setFocusable(false);
                getChildAt(2).setFocusable(false);
            }
        });
    }

    @Override
    public void setOnHoverListener(OnHoverListener l) {
        super.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                l.onHover(view, event);
            } else { if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child.isHovered()) {
                        child.setOnHoverListener(
                                (v, e) -> {
                                    if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                                        if (!view.isHovered()) {
                                            l.onHover(view, e);
                                        }
                                    }
                                    return false;
                                }
                        );
                        return false;
                    }
                }
                l.onHover(view, event);
            }}
            return false;
        });
    }
}
