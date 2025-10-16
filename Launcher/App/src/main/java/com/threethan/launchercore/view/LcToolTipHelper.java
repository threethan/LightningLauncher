package com.threethan.launchercore.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.threethan.launcher.R;

public abstract class LcToolTipHelper {
    private static final int DEFAULT_TOOLTIP_DELAY_MS = 250;

    public static void init(View view, AttributeSet attrs) {
        view.setForeground(view.getContext().getDrawable(R.drawable.lc_fg_focusable));
        if (attrs != null) {
            //noinspection resource
            TypedArray a = view.getContext().getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.LcText,
                    0, 0);
            try {
                int tResId = a.getResourceId(R.styleable.LcText_android_tooltipText, -1);
                init(view, tResId, null);

                if (tResId == -1 && a.getBoolean(R.styleable.LcText_defaultTooltip, false))
                    init(view, a.getResourceId(R.styleable.LcText_android_text, -1), null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    view.setTooltipText(null);
            } finally {
                a.recycle();
            }
        }
    }
    public static void init(View view, CharSequence tooltipText) {
        init(view, -1, tooltipText);
    }
    private static void init(View view, int textResId, CharSequence text) {
        if (textResId == -1 && (text == null || text == "")) {return;}
        final PopupWindow[] popupWindow = {null};
        @SuppressLint("SetTextI18n") Runnable showToolTip = () -> {
            if (popupWindow[0] != null && popupWindow[0].isShowing()) return;
            LayoutInflater inflater = (LayoutInflater) view.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            @SuppressLint("InflateParams") View tooltipView = inflater.inflate(R.layout.lc_tooltip, null);
            TextView textView = tooltipView.findViewById(R.id.tooltipText);
            if (textResId == -1) textView.setText(text);
            else textView.setText(textResId);
            if (view instanceof TextView textView1) {
                if (textView1.getLayout().getEllipsisCount(0) > 0)
                    textView.setText(textView.getText().toString() + '\n' + textView1.getText());
            }
            if (popupWindow[0] == null) {
                popupWindow[0] = new PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                popupWindow[0].setTouchable(false);
                popupWindow[0].setFocusable(false);
            }
            tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            popupWindow[0].showAsDropDown(view, view.getWidth()/2-tooltipView.getMeasuredWidth()/2, -5);
        };
        final Handler handler = new Handler(Looper.getMainLooper());
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                handler.postDelayed(showToolTip, DEFAULT_TOOLTIP_DELAY_MS);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                handler.removeCallbacks(showToolTip);
                if (popupWindow[0] != null && popupWindow[0].isShowing()) {
                    popupWindow[0].dismiss();
                }
            }
            return false;
        });

        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                view.postDelayed(() -> {
                    if (view.isFocused() && view.isFocusable()
                            && view.getVisibility() == View.VISIBLE
                            && view.getWindowToken() != null
                    ) showToolTip.run();
                }, 1250);
            }
            else {
                handler.removeCallbacks(showToolTip);
                if (popupWindow[0] != null && popupWindow[0].isShowing()) {
                    popupWindow[0].dismiss();
                }
            }
        });
    }
}

