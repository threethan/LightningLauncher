package com.threethan.launcher.activity.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.StringRes;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.LaunchExt;
import com.threethan.launcher.helper.QuestGameTuner;

import java.util.Random;

public class SettingsFlipper extends FocusableViewFlinger {
    public SettingsFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setIndicator(new Indicator(
                2.3F, 0, 3.5F, 17, 14,
                Indicator.VAlignment.BOTTOM, Indicator.HAlignment.RIGHT,
                Color.WHITE, Indicator.Style.ALPHA
        ));
        post(() -> {
            findViewById(R.id.flipperPage0).setOnClickListener(l
                    -> openUrl(context, R.string.flipper_page_0_link));
            findViewById(R.id.flipperPage1).setOnClickListener(l
                    -> openUrl(context, R.string.flipper_page_1_link));
            findViewById(R.id.flipperPageTuner).setOnClickListener(l
                    -> QuestGameTuner.openInfoDialog());

            setAutoAdvanceDelayMs(25);
            setAutoAdvance(true);
            setAutoAdvanceDelayMs(8000);

            setCurrentScreenNow(new Random().nextInt() % getScreenCount());
        });
    }

    private void openUrl(Context context, @StringRes int urlResId) {
        LaunchExt.launchUrl(getActivity(), context.getResources().getString(urlResId), false);
    }

    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }

}
