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
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
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
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;
import com.android.launcher3.quickspace.views.DateTextView;

public class QuickSpaceView extends FrameLayout implements AnimatorUpdateListener, Runnable, OnDataListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

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
    public TextView mEventTitle;
    public ViewGroup mWeatherContent;
    public ImageView mWeatherIcon;
    public TextView mWeatherTemp;

    public boolean mIsQuickEvent;
    public boolean mFinishedInflate;
    public boolean mWeatherAvailable;

    private QuickSpaceActionReceiver mActionReceiver;
    public QuickspaceController mController;

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
        }
        mWeatherAvailable = mController.isWeatherAvailable();
        getQuickSpaceView();
        if (mIsQuickEvent) {
            loadDoubleLine();
        } else {
            loadSingleLine();
        }
    }

    public final void loadDoubleLine() {
        setBackgroundResource(mQuickspaceBackgroundRes);
        mEventTitle.setText(mController.getEventController().getTitle());
        mEventTitle.setEllipsize(TruncateAt.END);
        mEventTitleSub.setText(mController.getEventController().getActionTitle());
        mEventTitleSub.setEllipsize(TruncateAt.MARQUEE);
        mEventTitleSub.setMarqueeRepeatLimit(3);
        mEventTitleSub.setSelected(true);
        mEventTitleSub.setOnClickListener(mController.getEventController().getAction());
        mEventSubIcon.setImageTintList(mColorStateList);
        mEventSubIcon.setImageResource(mController.getEventController().getActionIcon());
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
    }

    public final void loadSingleLine() {
        LayoutTransition transition = mQuickspaceContent.getLayoutTransition();
        mQuickspaceContent.setLayoutTransition(transition == null ? new LayoutTransition() : null);
        setBackgroundResource(0);
        bindWeather(mWeatherContent, mWeatherTemp, mWeatherIcon);
        bindClock(false);
    }

    public final void bindClock(boolean forced) {
        mClockView.setVisibility(View.VISIBLE);
        mClockView.setOnClickListener(mActionReceiver.getCalendarAction());
        if (forced) {
            mClockView.reloadDateFormat(true);
        }
    }

    public final void bindWeather(View container, TextView title, ImageView icon) {
        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        mWeatherAvailable = mController.isWeatherAvailable();
        if (mWeatherAvailable) {
            container.setVisibility(View.VISIBLE);
            container.setOnClickListener(hasGoogleApp ? mActionReceiver.getWeatherAction() : null);
            title.setText(mController.getWeatherTemp());
            icon.setImageIcon(mController.getWeatherIcon());
            return;
        }
        container.setVisibility(View.GONE);
    }

    public void reloadConfiguration() {
        if (!mIsQuickEvent) {
            bindClock(true);
        }
    }

    public final void loadViews() {
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
    }

    public void prepareLayout() {
        int indexOfChild = indexOfChild(mQuickspaceContent);
        removeView(mQuickspaceContent);
        addView(LayoutInflater.from(getContext()).inflate(mIsQuickEvent ?
                R.layout.quickspace_doubleline :
                R.layout.quickspace_singleline, this, false), indexOfChild);
        loadViews();
    }

    public void getQuickSpaceView() {
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
        if (mController != null && mFinishedInflate) {
            mController.addListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mController != null) {
            mController.removeListener(this);
        }
    }

    public boolean isPackageEnabled(String pkgName, Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        mFinishedInflate = true;
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
        if (isAttachedToWindow()) {
            if (mController != null) {
                mController.addListener(this);
            }
        }
    }

    @Override
    public void onLayout(boolean b, int n, int n2, int n3, int n4) {
        super.onLayout(b, n, n2, n3, n4);
        //mEventTitle.setText(cn); Todo: set the event info here
    }

    public void onPause() {
        mHandler.removeCallbacks(this);
        mController.onPause();
    }

    public void onResume() {
        mController.onResume();
    }

    public void run() {
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }

}
