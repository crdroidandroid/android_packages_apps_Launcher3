/*
 * Copyright (C) 2018-2021 crDroid Android Project
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

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.quickspace.views.DateTextView;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

import com.android.internal.util.crdroid.OmniJawsClient;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

public class QuickSpaceView extends FrameLayout implements ValueAnimator.AnimatorUpdateListener,
          OmniJawsClient.OmniJawsObserver {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    protected ContentResolver mContentResolver;

    private BubbleTextView mBubbleTextView;
    private DateTextView mClockView;
    private ImageView mWeatherIcon;
    private String mWeatherLabel;
    private TextView mWeatherTemp;
    private View mSeparator;
    private ViewGroup mQuickspaceContent;
    private ViewGroup mWeatherContent;

    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.PackageInfo mPackageInfo;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private boolean mUpdatesEnabled;

    private QuickSpaceActionReceiver mActionReceiver;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mWeatherClient = new OmniJawsClient(context);
        mWeatherClient.addSettingsObserver();
        mWeatherClient.addObserver(this);
        mActionReceiver = new QuickSpaceActionReceiver(context);
    }

    private void loadSingleLine() {
        setBackgroundResource(0);
        boolean hasGoogleApp = isPackageEnabled("com.google.android.googlequicksearchbox", getContext());
        mClockView.setOnClickListener(mActionReceiver.getCalendarAction());
        if (!mWeatherClient.isOmniJawsEnabled()) {
            mWeatherContent.setVisibility(View.GONE);
            mSeparator.setVisibility(View.GONE);
            Log.d(TAG, "WeatherProvider is unavailable");
            return;
        }
        if (mWeatherInfo == null) {
            mWeatherContent.setVisibility(View.GONE);
            mSeparator.setVisibility(View.GONE);
            Log.d(TAG, "WeatherInfo is null");
            return;
        }

        if (mPackageInfo == null) {
            mWeatherContent.setVisibility(View.GONE);
            mSeparator.setVisibility(View.GONE);
            Log.d(TAG, "PackageInfo is null");
            return;
        }

        Icon conditionIcon = Icon.createWithResource(mPackageInfo.packageName, mPackageInfo.resourceID);
        mSeparator.setVisibility(View.VISIBLE);
        mWeatherContent.setVisibility(View.VISIBLE);
        mWeatherTemp.setText(mWeatherLabel);
        mWeatherTemp.setOnClickListener(hasGoogleApp ? mActionReceiver.getWeatherAction() : null);
        mWeatherIcon.setImageIcon(conditionIcon);
    }

    private void loadViews() {
        mClockView = findViewById(R.id.clock_view);
        mQuickspaceContent = findViewById(R.id.quickspace_content);
        mSeparator = findViewById(R.id.separator);
        mWeatherIcon = findViewById(R.id.weather_icon);
        mWeatherContent = findViewById(R.id.weather_content);
        mWeatherTemp = findViewById(R.id.weather_temp);

        setTypeface(mClockView, mWeatherTemp);
    }

    private void setTypeface(TextView... views) {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/GoogleSans-Regular.ttf");
        for (TextView view : views) {
            if (view != null) {
                view.setTypeface(tf);
            }
        }
    }

    private void initListeners() {
        loadSingleLine();
    }

    public void getQuickSpaceView() {
        initListeners();
        if (mQuickspaceContent.getVisibility() != View.VISIBLE) {
            mQuickspaceContent.setVisibility(View.VISIBLE);
            mQuickspaceContent.setAlpha(0.0f);
            mQuickspaceContent.animate().setDuration(200).alpha(1.0f);
        }
    }

    @Override
    public void weatherUpdated() {
        Log.i(TAG, "weatherUpdated");
        queryAndUpdateWeather();
        loadSingleLine();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        mSeparator.setVisibility(View.GONE);
        mWeatherContent.setVisibility(View.GONE);
        mWeatherInfo = null;
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        if (mWeatherClient.isOmniJawsEnabled()) {
            updateWeather();
        }
        loadSingleLine();
    }

    private void queryAndUpdateWeather() {
        if (DEBUG) Log.d(TAG, "queryAndUpdateWeather.isOmniJawsEnabled " + mWeatherClient.isOmniJawsEnabled());
        mWeatherInfo = null;
        updateWeather();
    }

    private void updateWeather() {
        try {
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            if (mWeatherInfo != null) {
                Drawable conditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                mPackageInfo = mWeatherClient.getPackageInfo();
                mWeatherLabel = mWeatherInfo.temp + mWeatherInfo.tempUnits;
            }
        } catch(Exception e) {
            // Do nothing
        }
    }

    public void onAnimationUpdate(final ValueAnimator valueAnimator) {
        invalidate();
    }

    public boolean isPackageEnabled(String pkgName, Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        mContentResolver = getContext().getContentResolver();
        mBubbleTextView = findViewById(R.id.dummyBubbleTextView);
        mBubbleTextView.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        mBubbleTextView.setContentDescription("");
        updateSettings();
    }

    public void onResume() {
        Log.d(TAG, "onResume");
        updateSettings();
    }

    @Override
    public void setPadding(final int n, final int n2, final int n3, final int n4) {
        super.setPadding(0, 0, 0, 0);
    }

}
