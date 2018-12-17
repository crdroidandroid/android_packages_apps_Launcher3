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

import android.animation.ValueAnimator;
import android.content.ComponentName;
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

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

public class QuickSpaceView extends FrameLayout implements ValueAnimator.AnimatorUpdateListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    private BubbleTextView mBubbleTextView;
    private DateTextView mClockView;
    private ViewGroup mQuickspaceContent;

    private QuickSpaceActionReceiver mActionReceiver;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        mActionReceiver = new QuickSpaceActionReceiver(context);
    }

    private void loadSingleLine() {
        setBackgroundResource(0);
        mClockView.setOnClickListener(mActionReceiver.getCalendarAction());
    }

    private void loadViews() {
        mClockView = findViewById(R.id.clock_view);
        mQuickspaceContent = findViewById(R.id.quickspace_content);
        setTypeface(mClockView);
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
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        loadSingleLine();
    }

    protected void onFinishInflate() {
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
