package com.threethan.launcher.activity.dialog;

import android.app.AlertDialog;
import android.content.Context;

public abstract class AbstractDialog<T extends Context> {
    public abstract AlertDialog show();
    protected final T context;
    public AbstractDialog(T context) {
        this.context = context;
    }
}
