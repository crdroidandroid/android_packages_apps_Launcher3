/*
 * Copyright (C) 2018 CypherOS
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
package com.android.launcher3.quickspace;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri.Builder;
import android.os.Handler;
import android.os.Process;
import android.provider.CalendarContract;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherTab;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;
import com.android.launcher3.quickspace.views.DateTextView;

public class QuickSpaceView extends FrameLayout implements AnimatorUpdateListener, Runnable, OnDataListener {

    public final ColorStateList mColorStateList;
    public BubbleTextView mBubbleTextView;
    public final Handler mHandler;
    public final int mQuickspaceBackgroundRes;

    public DateTextView mClockView;
    public ViewGroup mQuickspaceContent;
    public ImageView mEventSubIcon;
    public TextView mEventTitleSub;
    public ViewGroup mWeatherContentSub;
    public ImageView mWeatherIconSub;
    public TextView mWeatherTempSub;
    public View mTitleSeparator;
    public TextView mEventTitle;
    public ViewGroup mWeatherContent;
    public ImageView mWeatherIcon;
    public TextView mWeatherTemp;

    public boolean mIsQuickEvent;
    public boolean mWeatherAvailable;
    private boolean mListening;

    private QuickSpaceActionReceiver mActionReceiver;
    public QuickspaceController mController;

    private static final int FONT_NORMAL = 0;
    private static final int FONT_ITALIC = 1;
    private static final int FONT_BOLD = 2;
    private static final int FONT_BOLD_ITALIC = 3;
    private static final int FONT_LIGHT = 4;
    private static final int FONT_LIGHT_ITALIC = 5;
    private static final int FONT_THIN = 6;
    private static final int FONT_THIN_ITALIC = 7;
    private static final int FONT_CONDENSED = 8;
    private static final int FONT_CONDENSED_ITALIC = 9;
    private static final int FONT_CONDENSED_LIGHT = 10;
    private static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    private static final int FONT_CONDENSED_BOLD = 12;
    private static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    private static final int FONT_MEDIUM = 14;
    private static final int FONT_MEDIUM_ITALIC = 15;
    private static final int FONT_BLACK = 16;
    private static final int FONT_BLACK_ITALIC = 17;
    private static final int FONT_DANCINGSCRIPT = 18;
    private static final int FONT_DANCINGSCRIPT_BOLD = 19;
    private static final int FONT_COMINGSOON = 20;
    private static final int FONT_NOTOSERIF = 21;
    private static final int FONT_NOTOSERIF_ITALIC = 22;
    private static final int FONT_NOTOSERIF_BOLD = 23;
    private static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mActionReceiver = new QuickSpaceActionReceiver(context);
        mController = new QuickspaceController(context);
        mHandler = new Handler();
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
    }

    @Override
    public void onDataUpdated() {
        mController.getEventController().initQuickEvents();
        if (mIsQuickEvent != mController.isQuickEvent()) {
            mIsQuickEvent = mController.isQuickEvent();
            prepareLayout();
            return;
        }
        loadQuickEvent();
    }

    private final void loadQuickEvent() {
        if (mController.getEventController() == null) return;
        getQuickSpaceView();
        if (mIsQuickEvent) {
            loadDoubleLine();
        } else {
            loadSingleLine();
        }
    }

    private final void loadDoubleLine() {
        setBackgroundResource(mQuickspaceBackgroundRes);
        mEventTitle.setText(mController.getEventController().getTitle());
        mEventTitle.setEllipsize(TruncateAt.END);
        mEventTitleSub.setText(mController.getEventController().getActionTitle());
        mEventTitleSub.setEllipsize(TruncateAt.END);
        mEventTitleSub.setOnClickListener(mController.getEventController().getAction());
        mEventSubIcon.setImageTintList(mColorStateList);
        mEventSubIcon.setImageResource(mController.getEventController().getActionIcon());
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
    }

    private final void loadSingleLine() {
        LayoutTransition transition = mQuickspaceContent.getLayoutTransition();
        mQuickspaceContent.setLayoutTransition(transition == null ? new LayoutTransition() : null);
        setBackgroundResource(0);
        bindWeather(mWeatherContent, mWeatherTemp, mWeatherIcon);
        bindClockAndSeparator(false);
    }

    private final void bindClockAndSeparator(boolean forced) {
        boolean hasGoogleCalendar = LauncherAppState.getInstanceNoCreate().isCalendarAppAvailable();
        mClockView.setVisibility(View.VISIBLE);
        mClockView.setOnClickListener(hasGoogleCalendar ? mActionReceiver.getCalendarAction() : null);
        if (forced) {
            mClockView.reloadDateFormat();
        }
        mTitleSeparator.setVisibility(mWeatherAvailable ? View.VISIBLE : View.GONE);
    }

    private final void bindWeather(View container, TextView title, ImageView icon) {
        boolean hasGoogleApp = LauncherAppState.getInstanceNoCreate().isSearchAppAvailable();
        mWeatherAvailable = Utilities.isWeatherEnabled(mContext) && mController.isWeatherAvailable();
        if (mWeatherAvailable) {
            container.setVisibility(View.VISIBLE);
            container.setOnClickListener(hasGoogleApp ? mActionReceiver.getWeatherAction() : null);
            if (Utilities.useAlternativeQuickspaceUI(getContext())) {
                if (mIsQuickEvent) {
                    title.setText(mController.getWeatherTemp() + " · ");
                } else {
                    title.setText(" · " + mController.getWeatherTemp());
                }
            } else {
                title.setText(mController.getWeatherTemp());
            }
            icon.setImageIcon(mController.getWeatherIcon());
            return;
        }
        container.setVisibility(View.GONE);
    }

    private void reloadConfiguration() {
        if (!mIsQuickEvent) {
            bindClockAndSeparator(true);
        }
    }

    private final void loadViews() {
        mEventTitle = (TextView) findViewById(R.id.quick_event_title);
        mEventTitleSub = (TextView) findViewById(R.id.quick_event_title_sub);
        mEventSubIcon = (ImageView) findViewById(R.id.quick_event_icon_sub);
        mWeatherIcon = (ImageView) findViewById(R.id.weather_icon);
        mWeatherIconSub = (ImageView) findViewById(R.id.quick_event_weather_icon);
        mQuickspaceContent = (ViewGroup) findViewById(R.id.quickspace_content);
        mWeatherContent = (ViewGroup) findViewById(R.id.weather_content);
        mWeatherContentSub = (ViewGroup) findViewById(R.id.quick_event_weather_content);
        mWeatherTemp = (TextView) findViewById(R.id.weather_temp);
        mWeatherTempSub = (TextView) findViewById(R.id.quick_event_weather_temp);
        mClockView = (DateTextView) findViewById(R.id.clock_view);
        mTitleSeparator = findViewById(R.id.separator);
        setTypeface(mEventTitle, mEventTitleSub, mWeatherTemp, mWeatherTempSub, mClockView);
        loadQuickEvent();
    }

    private void setTypeface(TextView... views) {
        Typeface tf;
        switch (Utilities.getDateStyleFont(getContext())) {
            case FONT_NORMAL:
            default:
                tf = Typeface.create("sans-serif", Typeface.NORMAL);
                break;
            case FONT_ITALIC:
                tf = Typeface.create("sans-serif", Typeface.ITALIC);
                break;
            case FONT_BOLD:
                tf = Typeface.create("sans-serif", Typeface.BOLD);
                break;
            case FONT_BOLD_ITALIC:
                tf = Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
                break;
            case FONT_LIGHT:
                tf = Typeface.create("sans-serif-light", Typeface.NORMAL);
                break;
            case FONT_LIGHT_ITALIC:
                tf = Typeface.create("sans-serif-light", Typeface.ITALIC);
                break;
            case FONT_THIN:
                tf = Typeface.create("sans-serif-thin", Typeface.NORMAL);
                break;
            case FONT_THIN_ITALIC:
                tf = Typeface.create("sans-serif-thin", Typeface.ITALIC);
                break;
            case FONT_CONDENSED:
                tf = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
                break;
            case FONT_CONDENSED_ITALIC:
                tf = Typeface.create("sans-serif-condensed", Typeface.ITALIC);
                break;
            case FONT_CONDENSED_LIGHT:
                tf = Typeface.create("sans-serif-condensed-light", Typeface.NORMAL);
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                tf = Typeface.create("sans-serif-condensed-light", Typeface.ITALIC);
                break;
            case FONT_CONDENSED_BOLD:
                tf = Typeface.create("sans-serif-condensed", Typeface.BOLD);
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                tf = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
                break;
            case FONT_MEDIUM:
                tf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                break;
            case FONT_MEDIUM_ITALIC:
                tf = Typeface.create("sans-serif-medium", Typeface.ITALIC);
                break;
            case FONT_BLACK:
                tf = Typeface.create("sans-serif-black", Typeface.NORMAL);
                break;
            case FONT_BLACK_ITALIC:
                tf = Typeface.create("sans-serif-black", Typeface.ITALIC);
                break;
            case FONT_DANCINGSCRIPT:
                tf = Typeface.create("cursive", Typeface.NORMAL);
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                tf = Typeface.create("cursive", Typeface.BOLD);
                break;
            case FONT_COMINGSOON:
                tf = Typeface.create("casual", Typeface.NORMAL);
                break;
            case FONT_NOTOSERIF:
                tf = Typeface.create("serif", Typeface.NORMAL);
                break;
            case FONT_NOTOSERIF_ITALIC:
                tf = Typeface.create("serif", Typeface.ITALIC);
                break;
            case FONT_NOTOSERIF_BOLD:
                tf = Typeface.create("serif", Typeface.BOLD);
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                tf = Typeface.create("serif", Typeface.BOLD_ITALIC);
                break;
        }
        for (TextView view : views) {
            if (view != null) {
                view.setTypeface(tf);
                view.setAllCaps(Utilities.isDateStyleUppercase(getContext()));
                view.setLetterSpacing(Utilities.getDateStyleTextSpacing(getContext()));
            }
        }
    }

    private void prepareLayout() {
        int indexOfChild = indexOfChild(mQuickspaceContent);
        removeView(mQuickspaceContent);
        if (Utilities.useAlternativeQuickspaceUI(getContext())) {
            addView(LayoutInflater.from(getContext()).inflate(mIsQuickEvent ?
                    R.layout.quickspace_alternate_double :
                    R.layout.quickspace_alternate_single, this, false), indexOfChild);
        } else {
            addView(LayoutInflater.from(getContext()).inflate(mIsQuickEvent ?
                    R.layout.quickspace_doubleline :
                    R.layout.quickspace_singleline, this, false), indexOfChild);
        }

        loadViews();
    }

    private void getQuickSpaceView() {
        if (!(mQuickspaceContent.getVisibility() == View.VISIBLE)) {
            mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0.0f);
            mQuickspaceContent.animate().setDuration(200).alpha(1.0f);
        }
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        invalidate();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mController != null && !mListening) {
            mListening = true;
            mController.addListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mController != null && mListening) {
            mController.removeListener(this);
            mListening = false;
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
    }

    @Override
    public void onLayout(boolean b, int n, int n2, int n3, int n4) {
        super.onLayout(b, n, n2, n3, n4);
        //mEventTitle.setText(cn); Todo: set the event info here
    }

    public void onPause() {
        mHandler.removeCallbacks(this);
    }

    public void run() {
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }

}
