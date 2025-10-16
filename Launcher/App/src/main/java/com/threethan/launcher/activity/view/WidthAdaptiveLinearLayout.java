package com.threethan.launcher.activity.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.threethan.launcher.R;
import com.threethan.launchercore.util.Platform;

public class WidthAdaptiveLinearLayout extends LinearLayout {
    private boolean hasUpdated = false;

    public WidthAdaptiveLinearLayout(Context context) {
        super(context);
    }

    public WidthAdaptiveLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public WidthAdaptiveLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /** @noinspection unused*/
    public WidthAdaptiveLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }
    private float widthThreshold = 400f;
    private float childMargin = 2f;
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            try (TypedArray a = getContext().obtainStyledAttributes(attrs, com.threethan.launcher.R.styleable.WidthAdaptiveLinearLayout)) {
                widthThreshold = a.getDimension(R.styleable.WidthAdaptiveLinearLayout_widthThreshold, widthThreshold);
                childMargin = a.getDimension(R.styleable.WidthAdaptiveLinearLayout_childMargin, childMargin);
            }
        }
        setClipToOutline(true);
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        boolean exceedsThreshold = getMeasuredWidth() > widthThreshold && !(widthThreshold < 0 && Platform.isTv());
        final int newOrientation = exceedsThreshold ? HORIZONTAL : VERTICAL;
        if (!hasUpdated || newOrientation != getOrientation()) {
            hasUpdated = true;
            for (int i = 0; i < getChildCount(); i++) {
                LayoutParams params = (LayoutParams) getChildAt(i).getLayoutParams();
                params.width = newOrientation == VERTICAL ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
                params.height = newOrientation == VERTICAL ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
                if (i != 0) {
                    params.setMarginStart(newOrientation == VERTICAL ? 0 : (int) (childMargin));
                    params.topMargin = newOrientation == VERTICAL ? (int) (childMargin) : 0;
                }
                getChildAt(i).setLayoutParams(params);
            }
            setOrientation(newOrientation);
            invalidate();
        }
    }
}
