/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Updated ViewFlinger created for https://github.com/threethan/LightningLauncher
package com.threethan.launcher.activity.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Scroller;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * A {@link ViewGroup} that shows one child at a time, allowing the user to swipe
 * horizontally to page between other child views. Based on <code>Workspace.java</code> in the
 * <code>Launcher.git</code> AOSP project.
 * <br/>
 * Sourced from <a href="https://gist.github.com/casidiablo/2036932">this Gist</a>
 * based on a defunct library.
 * <br/>
 * Updated by <a href="https://github.com/threethan">@threethan</a> 10/19/2024
 * - Updated to target new AndroidX without reflection or warnings on API 34
 * - Now supports scrolling via generic pointer input (such as horizontal mouse scroll)
 * - Now supports optionally supports looping and/or auto-advance
 * - Now supports adding customizable page indicators
 * - Foregrounds now render correctly, with support for the hover state
 * @noinspection unused
 */
public class ViewFlinger extends ViewGroup {
    private static final String TAG = "ViewFlinger";
    private static final Integer INVALID_SCREEN = null;

    /**
     * The velocity at which a fling gesture will cause us to snap to the next screen
     */
    private static final int SNAP_VELOCITY = 500;

    /**
     * The user needs to drag at least this much for it to be considered a fling gesture. This
     * reduces the chance of a random twitch sending the user to the next screen.
     */
    private static final int MIN_LENGTH_FOR_FLING = 100;

    private final int mDefaultScreen;

    private boolean mFirstLayout = true;
    private boolean mHasLaidOut = false;

    /** Sets a style of dots to indicate current page */
    public void setIndicator(Indicator dots) {
        this.mDots = dots;
        // Foreground is requires for rendering of indicator
        if (getForeground() == null) setForeground(new ColorDrawable(Color.TRANSPARENT));
    }

    private Indicator mDots = null;
    public static class Indicator {
        public enum HAlignment { LEFT, RIGHT, CENTER }
        public enum VAlignment { TOP, BOTTOM, MIDDLE }
        public enum Style {
            /** Fade indicators other than current page */
            ALPHA,
            /** Outline indicators other than current page */

            OUTLINE,
            /** Display only the current page at the specified length */
            WIDEN,
            /** Display only the current page at the specified length + fade others */
            WIDEN_ALPHA
        }

        private final float radius;
        private final float length;
        private final float separation;
        private final float xMargin;
        private final float yMargin;
        private final HAlignment hAlignment;
        private final VAlignment vAlignment;
        private final Paint paint;
        private final Style style;
        /**
         * Indicator dots for a ViewFlinger
         * @param radius Radius of the dots
         * @param length Length of indicators (if >0, indicators are pill-shaped)
         * @param separation Space between each dot
         * @param xMargin Margin between the dots and the left/right of the view
         * @param yMargin Margin between the dots and the top/bottom of the view
         * @param hAlignment Left, right, or center
         * @param vAlignment Top, bottom, or middle
         */
        public Indicator(float radius, float length, float separation, float xMargin, float yMargin,
                         VAlignment vAlignment, HAlignment hAlignment, @ColorInt int color, Style style) {

            this.radius = style == Style.OUTLINE ? radius * 0.75F : radius;
            this.length = length;
            this.separation = style == Style.OUTLINE ? radius * 0.25F + separation : separation;
            this.xMargin = xMargin;
            this.yMargin = yMargin;
            this.hAlignment = hAlignment;
            this.vAlignment = vAlignment;
            this.style = style;
            this.paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            if (style == Style.OUTLINE) {
                paint.setStrokeWidth(radius / 2);
            } else if (length != 0) {
                paint.setStrokeCap(Paint.Cap.ROUND);
            }
            paint.setColor(color);
        }
        /**
         * Indicator dots for a ViewFlinger
         * @param radius Radius of the dots
         * @param separation Space between each dot
         * @param hAlignment Left, right, or center
         * @param vAlignment Top, bottom, or middle
         */
        public Indicator(float radius, float separation, VAlignment vAlignment, HAlignment hAlignment) {
            this(radius, 0, separation, separation, separation, vAlignment, hAlignment,
                    Color.WHITE, Style.ALPHA);
        }

        private void draw(ViewFlinger f, Canvas canvas) {
            final float d = f.getContext().getResources().getDisplayMetrics().density;

            float x = 0, y = 0;
            final int count = f.getScreenCount();

            switch (hAlignment) {
                case LEFT -> x = (xMargin + radius);
                case RIGHT -> {
                    if (style == Style.WIDEN || style == Style.WIDEN_ALPHA)
                        x = f.getWidth() - d*count*(radius*2+separation) - d*(xMargin + radius + length);
                    else
                        x = f.getWidth() - d*count*(radius*2+separation + length) - d*(xMargin + radius);
                }
                case CENTER -> {
                    if (style == Style.WIDEN || style == Style.WIDEN_ALPHA)
                        x = f.getWidth()/2F - d*count*(radius + separation/2) - d*length;
                    else
                        x = f.getWidth()/2F - d*count*(radius + length + separation/2);
                }
            }
            switch (vAlignment) {
                case TOP -> y = (yMargin + radius);
                case BOTTOM -> y = f.getHeight() - d*(yMargin + radius);
                case MIDDLE -> y = f.getHeight()/2F;
            }

            final float frac = f.getCurrentScreenFraction();
            int targetScreen = (int) Math.floor((((frac + 0.5F * Math.signum(frac)) % count) + count) % count);
            for (int i = 0; i < f.getScreenCount(); i++) {
                x += d*radius*2 + d*separation;
                final boolean current = i == targetScreen;
                paint.setStrokeWidth(d*radius*2);

                switch (style) {
                    case ALPHA, WIDEN_ALPHA -> paint.setAlpha(current ? 255 : 128);
                    case OUTLINE -> {
                        if (length == 0) {
                            paint.setStrokeWidth(radius / 2);
                            paint.setStyle(current ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                        } else {
                            paint.setStrokeWidth(current ? d * radius * 2 : d * radius);
                        }
                    }
                }

                if (length == 0 || style == Style.WIDEN || style == Style.WIDEN_ALPHA && !current)
                    canvas.drawCircle(x, y, d*radius, paint);
                else {
                    canvas.drawLine(x, y, x + d*length, y, paint);
                    x += d*length;
                }
            }
        }
    }

    /** @return True if we can loop between the end and the start */
    public boolean isLooping() {
        return mLooping;
    }

    /**
     * Looping lets the last item wrap around to the first, and vise versa.
     * May not work correctly with less than three screens.
     * @param looping If true, enable looping.
     */
    public void setLooping(boolean looping) {
        this.mLooping = looping;
    }

    private boolean mLooping = true;

    public boolean isAutoAdvance() {
        return mAutoAdvance;
    }

    /**
     * Enable automatic, period advancing of views when there is no interaction.
     * @param autoAdvance If true, views will automatically advance at a set interval.
     */
    public void setAutoAdvance(boolean autoAdvance) {
        this.mAutoAdvance = autoAdvance;
        removeCallbacks(advanceRunnable);
        if (mAutoAdvance) postDelayed(advanceRunnable, mAutoAdvanceDelayMs);
    }

    public int getAutoAdvanceDelayMs() {
        return mAutoAdvanceDelayMs;
    }

    /**
     * Set the autoAdvance delay. If already auto-advancing, the delay will update on next flip or interaction.
     * @param autoAdvanceDelayMs Delay, in ms, between automatic flips.
     */
    public void setAutoAdvanceDelayMs(int autoAdvanceDelayMs) {
        this.mAutoAdvanceDelayMs = autoAdvanceDelayMs;
    }

    private final Runnable advanceRunnable = () -> {
        try {
            if (isAutoAdvance()) {
                scrollRight();
                setAutoAdvance(true);
            }
        } catch (Exception ignored) {}
    };

    private boolean mAutoAdvance = false;
    private int mAutoAdvanceDelayMs = 5000;
    private int mCurrentScreen;
    private Integer mNextScreen = INVALID_SCREEN;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    /**
     * X position of the active pointer when it was first pressed down.
     */
    private float mDownMotionX;

    /**
     * Y position of the active pointer when it was first pressed down.
     */
    private float mDownMotionY;

    /**
     * This view's X scroll offset when the active pointer was first pressed down.
     */
    private int mDownScrollX;

    private final static int TOUCH_STATE_REST = 0;
    private final static int TOUCH_STATE_SCROLLING = 1;

    private int mTouchState = TOUCH_STATE_REST;

    private boolean mAllowLongPress = true;

    private int mTouchSlop;
    private int mPagingTouchSlop;
    private int mMaximumVelocity;

    private static final int INVALID_POINTER = -1;

    private int mActivePointerId = INVALID_POINTER;

    private Drawable mSeparatorDrawable;

    private OnScreenChangeListener mOnScreenChangeListener;
    private OnScrollListener mOnScrollListener;

    private boolean mLocked;

    private int mDeferredScreenChange = -1;
    private boolean mDeferredScreenChangeFast = false;
    private boolean mDeferredNotify = false;

    private boolean mIgnoreChildFocusRequests;

    private final boolean mIsVerbose;

    public interface OnScreenChangeListener {
        void onScreenChanged(View newScreen, int newScreenIndex);

        void onScreenChanging(View newScreen, int newScreenIndex);
    }

    public interface OnScrollListener {
        void onScroll(float screenFraction);
    }

    /**
     * Used to inflate the com.google.android.ext.workspace.Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the com.google.android.ext.workspace.Workspace's
     *                customization values.
     */
    public ViewFlinger(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDefaultScreen = 0;
        mLocked = false;

        setHapticFeedbackEnabled(false);
        initWorkspace();

        mIsVerbose = Log.isLoggable(TAG, Log.VERBOSE);
    }

    /**
     * Initializes various states for this workspace.
     */
    private void initWorkspace() {
        mScroller = new Scroller(getContext());
        mCurrentScreen = mDefaultScreen;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
    }

    /**
     * Returns the index of the currently displayed screen.
     */
    public int getCurrentScreen() {
        if (mLooping) {
            final int screenCount = getScreenCount();
            return ((mCurrentScreen % screenCount) + screenCount) % screenCount;
        }
        return mCurrentScreen;
    }

    /**
     * Returns the number of screens currently contained in this Workspace.
     */
    public int getScreenCount() {
        int childCount = getChildCount();
        if (mSeparatorDrawable != null) {
            return (childCount + 1) / 2;
        }
        return childCount;
    }

    public View getScreenAt(int index) {
        if (mLooping) {
            int screenCount = getScreenCount();
            index = ((index % screenCount) + screenCount) % screenCount;
        }
        if (mSeparatorDrawable == null) return getChildAt(index);
        return getChildAt(index * 2);
    }

    int getScrollWidth() {
        int w = getWidth();
        if (mSeparatorDrawable != null) {
            w += mSeparatorDrawable.getIntrinsicWidth();
        }
        return w;
    }

    void handleScreenChangeCompletion(int currentScreen) {
        mCurrentScreen = currentScreen;
        View screen = getScreenAt(mCurrentScreen);
        //screen.requestFocus();
        try {
            screen.dispatchDisplayHint(VISIBLE);
            invalidate();
        } catch (NullPointerException e) {
            Log.e(TAG, "Caught NullPointerException", e);
        }

        mPointerScrollEnabled = true;
        notifyScreenChangeListener(mCurrentScreen, true);
    }

    void notifyScreenChangeListener(int whichScreen, boolean changeComplete) {
        if (mOnScreenChangeListener != null) {
            if (changeComplete)
                mOnScreenChangeListener.onScreenChanged(getScreenAt(whichScreen), whichScreen);
            else
                mOnScreenChangeListener.onScreenChanging(getScreenAt(whichScreen), whichScreen);
        }
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(getCurrentScreenFraction());
        }
    }

    /**
     * Registers the specified listener on each screen contained in this workspace.
     *
     * @param listener The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener listener) {
        //mLongClickListener = listener;
        final int count = getScreenCount();
        for (int i = 0; i < count; i++) {
            getScreenAt(i).setOnLongClickListener(listener);
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            if (mOnScrollListener != null) {
                mOnScrollListener.onScroll(getCurrentScreenFraction());
            }
            postInvalidate();
        } else if (mNextScreen != INVALID_SCREEN) {
            // The scroller has finished.
            if (mLooping) handleScreenChangeCompletion(mNextScreen);
            else handleScreenChangeCompletion(
                    Math.max(0, Math.min(mNextScreen, getScreenCount() - 1)));
            mNextScreen = INVALID_SCREEN;
        }
    }


    @Override
    public void onDrawForeground(@NonNull Canvas canvas) {
        canvas.setMatrix(new Matrix());
        if (mDots != null) mDots.draw(this, canvas);
        super.onDrawForeground(canvas);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        //boolean restore = false;
        //int restoreCount = 0;

        // ViewGroup.dispatchDraw() supports many features we don't need:
        // clip to padding, layout animation, animation listener, disappearing
        // children, etc. The following implementation attempts to fast-track
        // the drawing dispatch by drawing only what we know needs to be drawn.

        if (mLooping) requestLayout();

        boolean fastDraw = mTouchState != TOUCH_STATE_SCROLLING && mNextScreen == INVALID_SCREEN
                && mPointerScrollX == 0;
        // If we are not scrolling or flinging, draw only the current screen
        if (fastDraw) {
            if (getScreenAt(getCurrentScreen()) != null) {
                drawChild(canvas, getScreenAt(getCurrentScreen()), getDrawingTime());
            }
        } else {
            final long drawingTime = getDrawingTime();
            // If we are flinging, draw only the current screen and the target screen
            if (!mLooping && mNextScreen != null && mNextScreen >= 0 && mNextScreen < getScreenCount() &&
                    Math.abs(mCurrentScreen - mNextScreen) == 1) {
                drawChild(canvas, getScreenAt(mCurrentScreen), drawingTime);
                drawChild(canvas, getScreenAt(mNextScreen), drawingTime);
            } else {
                // If we are scrolling, draw all of our children
                final int count = getChildCount();
                for (int i = 0; i < count; i++) {
                    drawChild(canvas, getChildAt(i), drawingTime);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The children are given the same width and height as the workspace
        final int count = getChildCount();
        int mHeight = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (mSeparatorDrawable != null && (i & 1) == 1) {
                // separator
                getChildAt(i).measure(mSeparatorDrawable.getIntrinsicWidth(), heightMeasureSpec);
            } else {
                getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
                mHeight = Math.max(getChildAt(i).getMeasuredHeight(), mHeight);
            }
        }

        if (mHeight != Integer.MIN_VALUE) {
            setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), resolveSize(mHeight, heightMeasureSpec));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        if (mFirstLayout) {
            setHorizontalScrollBarEnabled(false);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (mSeparatorDrawable != null) {
                width += mSeparatorDrawable.getIntrinsicWidth();
            }
            scrollTo(mCurrentScreen * width, 0);
            setHorizontalScrollBarEnabled(true);
            mFirstLayout = false;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();

        if (mLooping) {
            final int childWidth = getScrollWidth();
            final int screenCount = getScreenCount();

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                int childLeft = childWidth * (i);
                final int mCalcScreen = Math.floorDiv(getScrollX(), childWidth);

                int mCalcEnd = mCalcScreen + screenCount - 1;
                int screenWidth = childWidth * screenCount;

                if (mCalcScreen > i) {
                    childLeft += screenWidth * ((mCalcScreen - i + screenCount - 1) / screenCount);
                } else if (mCalcEnd < i) {
                    childLeft -= screenWidth * ((i - mCalcEnd + screenCount - 1) / screenCount);
                }
                child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
            }
        } else {
            int childLeft = 0;
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() != View.GONE) {
                    final int childWidth = child.getMeasuredWidth();
                    child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
                    childLeft += childWidth;
                }
            }
        }

        mHasLaidOut = true;
        if (mDeferredScreenChange >= 0) {
            snapToScreen(mDeferredScreenChange, mDeferredScreenChangeFast, mDeferredNotify);
            mDeferredScreenChange = -1;
            mDeferredScreenChangeFast = false;
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int screen = indexOfChild(child);
        if (mIgnoreChildFocusRequests && !mScroller.isFinished()) {
            Log.w(TAG, "Ignoring child focus request: request " + mCurrentScreen + " -> " + screen);
            return false;
        }
        if (screen != mCurrentScreen || !mScroller.isFinished()) {
            snapToScreen(screen);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusableScreen;
        if (mNextScreen != INVALID_SCREEN) {
            focusableScreen = mNextScreen;
        } else {
            focusableScreen = mCurrentScreen;
        }
        View v = getScreenAt(focusableScreen);
        return v != null && v.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == View.FOCUS_LEFT) {
            if (mLooping || mCurrentScreen > 0) {
                snapToScreen(mCurrentScreen - 1);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (mLooping || mCurrentScreen < getScreenCount() - 1) {
                snapToScreen(mCurrentScreen + 1);
                return true;
            }
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View focusableSourceScreen = null;
        if (mLooping || mCurrentScreen >= 0 && mCurrentScreen < getScreenCount()) {
            focusableSourceScreen = getScreenAt(mCurrentScreen);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentScreen > 0 || mLooping) {
                focusableSourceScreen = getScreenAt(mCurrentScreen - 1);
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (mCurrentScreen < getScreenCount() - 1 || mLooping) {
                focusableSourceScreen = getScreenAt(mCurrentScreen + 1);
            }
        }

        if (focusableSourceScreen != null) {
            focusableSourceScreen.addFocusables(views, direction, focusableMode);
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only pass that along if
     * it's on the current screen.
     * <p/>
     * This happens when live folders requery, and if they're off screen, they end up calling
     * requestFocus, which pulls it on screen.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getScreenAt(mCurrentScreen);
        View v = focused;
        ViewParent parent;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            parent = v.getParent();
            if (parent instanceof View) {
                v = (View) v.getParent();
            } else {
                return;
            }
        }
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        setAutoAdvance(isAutoAdvance()); // Reset auto-advance timer
        if (ev.getAction() == MotionEvent.ACTION_HOVER_ENTER) setHovered(true);
        if (ev.getAction() == MotionEvent.ACTION_HOVER_EXIT) setHovered(false);
        return false;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    private float mPointerScrollX = 0F;
    private boolean mPointerScrollEnabled = true;
    ValueAnimator mPointerResetAnimator;
    @Override
    protected boolean dispatchGenericPointerEvent(MotionEvent ev) {
        setAutoAdvance(isAutoAdvance()); // Reset auto-advance timer
        final boolean actionScroll = ev.getActionMasked() == MotionEvent.ACTION_SCROLL;
        if (mPointerScrollEnabled && !mLocked && actionScroll) {
            if (mPointerResetAnimator != null) mPointerResetAnimator.cancel();
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                mPointerScrollX = 0;
            } else {
                final float hScroll = ev.getAxisValue(MotionEvent.AXIS_HSCROLL);

                mPointerScrollX += hScroll * getWidth() * 0.001F;

                final int scrollAmt = (int) (Math.pow(mPointerScrollX * 5, 2)
                        * Math.signum(mPointerScrollX));

                final int scrollStart = getScreenAt(mCurrentScreen).getLeft();

                final View lastChild = getChildAt(getChildCount() - 1);
                final int maxScrollX = lastChild.getRight() - getWidth();

                if (mPointerResetAnimator != null) mPointerResetAnimator.cancel();

                // Cap pointer scroll
                if (!isLooping()) {
                    if (getCurrentScreen() >= getScreenCount() -1 && mPointerScrollX > 0)
                        mPointerScrollX = 0;
                    if (getCurrentScreen() <= 0 && mPointerScrollX < 0)
                        mPointerScrollX = 0;
                }
                if (Math.abs(scrollAmt) > getWidth() * 0.3F) {
                    if (scrollAmt > 0) scrollRight();
                    else scrollLeft();
                    mPointerScrollX = 0;
                    mPointerScrollEnabled = false;
                } else {
                    if (mLooping) setScrollX(scrollStart + scrollAmt);
                    else setScrollX(Math.max(0, Math.min(maxScrollX, scrollStart + scrollAmt)));

                    mPointerResetAnimator = ValueAnimator.ofInt(scrollStart + scrollAmt,
                            scrollStart);
                    mPointerResetAnimator.setStartDelay(100);
                    mPointerResetAnimator.setDuration(500);
                    mPointerResetAnimator.addUpdateListener(l
                            -> setScrollX((int) l.getAnimatedValue()));
                    mPointerResetAnimator.start();
                }
                if (scrollAmt != 0 && mOnScrollListener != null) {
                    mOnScrollListener.onScroll(getCurrentScreenFraction());
                }
            }
        }
        return super.dispatchGenericPointerEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */

        setAutoAdvance(isAutoAdvance()); // Reset auto-advance timer
        // Begin tracking velocity even before we have intercepted touch events.
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if (mIsVerbose) {
            Log.v(TAG, "onInterceptTouchEvent: " + (ev.getAction() & MotionEvent.ACTION_MASK));
        }
        if (((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_MOVE)
                && (mTouchState == TOUCH_STATE_SCROLLING)) {
            if (mIsVerbose) {
                Log.v(TAG, "Intercepting touch events");
            }
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE -> {
                if (mLocked) {
                    // we're locked on the current screen, don't allow moving
                    break;
                }

                /*
                 * Locally do absolute value. mDownMotionX is set to the y value
                 * of the down event.
                 */
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                final int xDiff = (int) Math.abs(x - mDownMotionX);
                final int yDiff = (int) Math.abs(y - mDownMotionY);

                boolean xPaged = xDiff > mPagingTouchSlop;
                boolean xMoved = xDiff > mTouchSlop;
                boolean yMoved = yDiff > mTouchSlop;

                if (xMoved || yMoved) {
                    if (xPaged) {
                        // Scroll if the user moved far enough along the X axis
                        mTouchState = TOUCH_STATE_SCROLLING;
                    }
                    // Either way, cancel any pending longpress
                    if (mAllowLongPress) {
                        mAllowLongPress = false;
                        // Try canceling the long press. It could also have been scheduled
                        // by a distant descendant, so use the mAllowLongPress flag to block
                        // everything
                        final View currentScreen = getScreenAt(mCurrentScreen);
                        if (currentScreen != null) {
                            currentScreen.cancelLongPress();
                        }
                    }
                }
            }
            case MotionEvent.ACTION_DOWN -> {
                final float x = ev.getX();
                final float y = ev.getY();
                // Remember location of down touch
                mDownMotionX = x;
                mDownMotionY = y;
                mDownScrollX = getScrollX();
                mActivePointerId = ev.getPointerId(0);
                mAllowLongPress = true;

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST : TOUCH_STATE_SCROLLING;
            }
            case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // Release the drag
                mTouchState = TOUCH_STATE_REST;
                mAllowLongPress = false;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
            }
            case MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev);
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        boolean intercept = mTouchState != TOUCH_STATE_REST;
        if (mIsVerbose) {
            Log.v(TAG, "Intercepting touch events: " + intercept);
        }
        return intercept;
    }

    void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // OLD to-do: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mDownMotionX = ev.getX(newPointerIndex);
            mDownMotionX = ev.getY(newPointerIndex);
            mDownScrollX = getScrollX();
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int screen = indexOfChild(child);
        if (mSeparatorDrawable != null) {
            screen /= 2;
        }
        if (screen >= 0 && !isInTouchMode()) {
            snapToScreen(screen);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsVerbose) {
            Log.v(TAG, "onTouchEvent: " + (ev.getAction() & MotionEvent.ACTION_MASK));
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                // If being flinged and user touches, stop the fling. isFinished
                // will be false if being flinged.
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mDownMotionX = ev.getX();
                mDownMotionY = ev.getY();
                mDownScrollX = getScrollX();
                mActivePointerId = ev.getPointerId( 0);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mIsVerbose) {
                    Log.v(TAG, "mTouchState=" + mTouchState);
                }

                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    final float x = ev.getX(pointerIndex);

                    final View lastChild = getChildAt(getChildCount() - 1);
                    final int maxScrollX = lastChild.getRight() - getWidth();
                    if (mLooping) {
                        setScrollX(
                                (int) (mDownScrollX + mDownMotionX - x
                                ));
                    } else {
                        setScrollX(Math.max(0, Math.min(maxScrollX,
                                (int) (mDownScrollX + mDownMotionX - x
                                ))));
                    }
                    if (mOnScrollListener != null) {
                        mOnScrollListener.onScroll(getCurrentScreenFraction());
                    }

                } else if (mTouchState == TOUCH_STATE_REST) {
                    if (mLocked) {
                        // we're locked on the current screen, don't allow moving
                        break;
                    }

                    /*
                     * Locally do absolute value. mLastMotionX is set to the y value
                     * of the down event.
                     */
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    final float x = ev.getX(pointerIndex);
                    final float y = ev.getY(pointerIndex);
                    final int xDiff = (int) Math.abs(x - mDownMotionX);
                    final int yDiff = (int) Math.abs(y - mDownMotionY);

                    boolean xPaged = xDiff > mPagingTouchSlop;
                    boolean xMoved = xDiff > mTouchSlop;
                    boolean yMoved = yDiff > mTouchSlop;

                    if (xMoved || yMoved) {
                        if (xPaged) {
                            // Scroll if the user moved far enough along the X axis
                            mTouchState = TOUCH_STATE_SCROLLING;
                        }
                        // Either way, cancel any pending longpress
                        if (mAllowLongPress) {
                            mAllowLongPress = false;
                            // Try canceling the long press. It could also have been scheduled
                            // by a distant descendant, so use the mAllowLongPress flag to block
                            // everything
                            final View currentScreen = getScreenAt(mCurrentScreen);
                            if (currentScreen != null) {
                                currentScreen.cancelLongPress();
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mTouchState == TOUCH_STATE_SCROLLING) {
//                    mScrollEvt = false;
                    final int activePointerId = mActivePointerId;
                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    final float x = ev.getX(pointerIndex);
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    //Old TO-DO(minsdk8): int velocityX = (int) MotionEventUtils.getXVelocity(velocityTracker, activePointerId);
                    int velocityX = (int) velocityTracker.getXVelocity();
                    boolean isFling = Math.abs(mDownMotionX - x) > MIN_LENGTH_FOR_FLING;

                    final float scrolledPos = getCurrentScreenFraction();
                    final int whichScreen = Math.round(scrolledPos);

                    if (isFling && mIsVerbose) {
                        Log.v(TAG, "isFling, whichScreen=" + whichScreen
                                + " scrolledPos=" + scrolledPos
                                + " mCurrentScreen=" + mCurrentScreen
                                + " velocityX=" + velocityX);
                    }
                    if (isFling && velocityX > SNAP_VELOCITY && (mCurrentScreen > 0 || mLooping)) {
                        // Fling hard enough to move left
                        // Don't fling across more than one screen at a time.
                        final int bound = scrolledPos <= whichScreen ?
                                mCurrentScreen - 1 : mCurrentScreen;
                        if (mLooping) snapToScreen(whichScreen);
                        else snapToScreen(Math.min(whichScreen, bound));
                    } else if (isFling && velocityX < -SNAP_VELOCITY &&
                            (mCurrentScreen < getChildCount() - 1 || mLooping)) {
                        // Fling hard enough to move right
                        // Don't fling across more than one screen at a time.
                        final int bound = scrolledPos >= whichScreen ?
                                mCurrentScreen + 1 : mCurrentScreen;
                        if (mLooping) snapToScreen(whichScreen);
                        else snapToScreen(Math.max(whichScreen, bound));
                    } else {
                        snapToDestination();
                    }
                }
                // Intentially fall through to cancel

            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_REST;
                mActivePointerId = INVALID_POINTER;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        return true;
    }

    /**
     * Returns the current scroll position as a float value from 0 to the number of screens.
     * Fractional values indicate that the user is mid-scroll or mid-fling, and whole values
     * indicate that the Workspace is currently snapped to a screen.
     */
    float getCurrentScreenFraction() {
        if (!mHasLaidOut) {
            return mCurrentScreen;
        }
        final int scrollX = getScrollX();
        final int screenWidth = getWidth();
        return (float) scrollX / screenWidth;
    }

    void snapToDestination() {
        final int screenWidth = getScrollWidth();
        final int sign = (int) Math.signum(getScrollX());
        final int whichScreen = (getScrollX() + (screenWidth / 2 * sign)) / screenWidth;

        snapToScreen(whichScreen);
    }

    public void snapToScreen(int whichScreen) {
        snapToScreen(whichScreen, false, true);
    }

    void snapToScreen(int whichScreen, boolean fast, boolean notify) {
        if (!mHasLaidOut) { // Can't handle scrolling until we are laid out.
            mDeferredScreenChange = whichScreen;
            mDeferredScreenChangeFast = fast;
            mDeferredNotify = notify;
            return;
        }

        if (mIsVerbose) {
            Log.v(TAG, "Snapping to screen: " + whichScreen);
        }

        if (!mLooping) whichScreen = Math.max(0, Math.min(whichScreen, getScreenCount() - 1));


        final boolean screenChanging =
                (mNextScreen != INVALID_SCREEN && mNextScreen != whichScreen) ||
                        (mCurrentScreen != whichScreen);

        mNextScreen = whichScreen;

        View focusedChild = getFocusedChild();

        final int newX = whichScreen * getScrollWidth();
        final int sX = getScrollX();
        final int delta = newX - sX;
        int duration = 300;
        awakenScrollBars(300);
        if (fast) {
            duration = 0;
        }

        if (mNextScreen != mCurrentScreen) {
            // make the current listview hide its filter popup
            final View screenAt = getScreenAt(mCurrentScreen);
            if (screenAt != null) {
                screenAt.dispatchDisplayHint(INVISIBLE);
            } else {
                Log.e(TAG, "Screen at index was null. mCurrentScreen = " + mCurrentScreen);
                return;
            }

            // showing the filter popup for the next listview needs to be delayed
            // until we've fully moved to that listview, since otherwise the
            // popup will appear at the wrong place on the screen
            //removeCallbacks(mFilterWindowEnabler);
            //postDelayed(mFilterWindowEnabler, duration + 10);

            // NOTE: moved to computeScroll and handleScreenChangeCompletion()
        }

        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        mScroller.startScroll(sX, 0, delta, 0, duration);
        if (screenChanging && notify) {
            notifyScreenChangeListener(mNextScreen, false);
        }
        invalidate();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final SavedState state = new SavedState(super.onSaveInstanceState());
        state.currentScreen = mCurrentScreen;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        if (savedState.currentScreen != -1) {
            snapToScreen(savedState.currentScreen, true, true);
        }
    }

    /**
     * @return True is long presses are still allowed for the current touch
     */
    boolean allowLongPress() {
        return mAllowLongPress;
    }

    /**
     * Register a callback to be invoked when the screen is changed, either programmatically or via
     * user interaction.  Will automatically trigger a callback.
     *
     * @param screenChangeListener The callback.
     */
    public void setOnScreenChangeListener(OnScreenChangeListener screenChangeListener) {
        setOnScreenChangeListener(screenChangeListener, true);
    }

    /**
     * Register a callback to be invoked when the screen is changed, either programmatically or via
     * user interaction.
     *
     * @param screenChangeListener The callback.
     * @param notifyImmediately    Whether to trigger a notification immediately
     */
    public void setOnScreenChangeListener(OnScreenChangeListener screenChangeListener,
                                          boolean notifyImmediately) {
        mOnScreenChangeListener = screenChangeListener;
        if (mOnScreenChangeListener != null && notifyImmediately) {
            mOnScreenChangeListener.onScreenChanged(getScreenAt(mCurrentScreen), mCurrentScreen);
        }
    }

    /**
     * Register a callback to be invoked when this Workspace is mid-scroll or mid-fling, either
     * due to user interaction or programmatic changes in the current screen index.
     *
     * @param scrollListener    The callback.
     * @param notifyImmediately Whether to trigger a notification immediately
     */
    public void setOnScrollListener(OnScrollListener scrollListener, boolean notifyImmediately) {
        mOnScrollListener = scrollListener;
        if (mOnScrollListener != null && notifyImmediately) {
            mOnScrollListener.onScroll(getCurrentScreenFraction());
        }
    }

    /**
     * Scrolls to the given screen.
     */
    public void setCurrentScreen(int screenIndex) {
        if (mLooping) snapToScreen(screenIndex);
        else snapToScreen(Math.max(0, Math.min(getScreenCount() - 1, screenIndex)));
    }

    /**
     * Scrolls to the given screen fast (no matter how large the scroll distance is)

     */
    public void setCurrentScreenNow(int screenIndex) {
        setCurrentScreenNow(screenIndex, true);
    }

    public void setCurrentScreenNow(int screenIndex, boolean notify) {
        if (mLooping) snapToScreen(screenIndex, true, notify);
        else snapToScreen(Math.max(0, Math.min(getScreenCount() - 1, screenIndex)),
                true, notify);
    }

    /**
     * Scrolls to the screen adjacent to the current screen on the left, if it exists. This method
     * is a no-op if the Workspace is currently locked.
     */
    public void scrollLeft() {
        if (mLocked) {
            return;
        }
        if (mScroller.isFinished()) {
            if (mLooping || mCurrentScreen > 0) {
                snapToScreen(mCurrentScreen - 1);
            }
        } else {
            if (mLooping || mNextScreen > 0) {
                snapToScreen(mNextScreen - 1);
            }
        }
    }

    /**
     * Scrolls to the screen adjacent to the current screen on the right, if it exists. This method
     * is a no-op if the Workspace is currently locked.
     */
    public void scrollRight() {
        if (mLocked) {
            return;
        }
        if (mScroller.isFinished()) {
            if (mLooping || mCurrentScreen < getChildCount() - 1) {
                snapToScreen(mCurrentScreen + 1);
            }
        } else {
            if (mLooping || mNextScreen < getChildCount() - 1) {
                snapToScreen(mNextScreen + 1);
            }
        }
    }

    /**
     * If set, invocations of requestChildRectangleOnScreen() will be ignored.
     */
    public void setIgnoreChildFocusRequests(boolean mIgnoreChildFocusRequests) {
        this.mIgnoreChildFocusRequests = mIgnoreChildFocusRequests;
    }

    public void markViewSelected(View v) {
        mCurrentScreen = indexOfChild(v);
    }

    /**
     * Locks the current screen, preventing users from changing screens by swiping.
     */
    public void lockCurrentScreen() {
        mLocked = true;
    }

    /**
     * Unlocks the current screen, if it was previously locked.
     */
    public void unlockCurrentScreen() {
        mLocked = false;
    }

    /**
     * Sets the resource ID of the separator drawable to use between adjacent screens.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public void setSeparator(int resId) {
        if (mSeparatorDrawable != null && resId == 0) {
            // remove existing separators
            mSeparatorDrawable = null;
            int num = getChildCount();
            for (int i = num - 2; i > 0; i -= 2) {
                removeViewAt(i);
            }
            requestLayout();
        } else if (resId != 0) {
            // add or update separators
            if (mSeparatorDrawable == null) {
                // add
                int numsep = getChildCount();
                int insertIndex = 1;
                //noinspection deprecation
                mSeparatorDrawable = getResources().getDrawable(resId);
                for (int i = 1; i < numsep; i++) {
                    View v = new View(getContext());
                    //noinspection deprecation
                    v.setBackgroundDrawable(mSeparatorDrawable);
                    //noinspection deprecation
                    LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.FILL_PARENT);
                    v.setLayoutParams(lp);
                    addView(v, insertIndex);
                    insertIndex += 2;
                }
                requestLayout();
            } else {
                // update
                //noinspection deprecation
                mSeparatorDrawable = getResources().getDrawable(resId);
                int num = getChildCount();
                for (int i = num - 2; i > 0; i -= 2) {
                    //noinspection deprecation
                    getChildAt(i).setBackgroundDrawable(mSeparatorDrawable);
                }
                requestLayout();
            }
        }
    }

    private static class SavedState extends BaseSavedState {
        int currentScreen = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentScreen = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentScreen);
        }

        public static final Creator<SavedState> CREATOR =
                new Creator<>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    public void addViewToFront(View v) {
        mCurrentScreen++;
        addView(v, 0);
    }

    public void removeViewFromFront() {
        mCurrentScreen--;
        removeViewAt(0);
    }

    public void addViewToBack(View v) {
        addView(v);
    }

    public void removeViewFromBack() {
        removeViewAt(getChildCount() - 1);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(getCurrentScreenFraction());
        }
    }
}
