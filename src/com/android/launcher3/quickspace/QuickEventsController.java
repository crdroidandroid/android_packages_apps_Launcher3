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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.Calendar;
import java.util.Random;
import java.util.Arrays;

public class QuickEventsController {

    private Context mContext;

    private String mEventTitle;
    private String mEventTitleSub;
    private OnClickListener mEventTitleSubAction = null;
    private int mEventSubIcon;

    private boolean mRunning;
    private boolean mRegistered;

    // Quotes
    private String[] mQuotes;
    private BroadcastReceiver mPSAListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateQuickEvents();
        }
    };

    public QuickEventsController(Context context) {
        mContext = context;
        initQuickEvents();
    }

    public void initQuickEvents() {
        mRunning = true;
        registerPSAListener();
        mRegistered = true;
        updateQuickEvents();
    }

    private void registerPSAListener() {
        if (mRegistered) return;
        IntentFilter psonalityIntent = new IntentFilter();
        psonalityIntent.addAction(Intent.ACTION_TIME_TICK);
        psonalityIntent.addAction(Intent.ACTION_TIME_CHANGED);
        psonalityIntent.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mContext.registerReceiver(mPSAListener, psonalityIntent);
    }

    private void unregisterPSAListener() {
        if (!mRegistered) return;
        mContext.unregisterReceiver(mPSAListener);
    }

    public void updateQuickEvents() {
        if (!mRunning) return;

        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    Launcher.getLauncher(mContext).startActivitySafely(view, intent, null);
                } catch (ActivityNotFoundException ex) {
                }
            }
        };

        int psaLength;
        mQuotes = mContext.getResources().getStringArray(R.array.quickspace_psa_random);
        psaLength = mQuotes.length - 1;
        mEventTitleSub = mQuotes[getLuckyNumber(0, psaLength)];

        // Clean the onClick event to avoid any weird behavior
        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                // haha yes
            }
        };

        switch (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            case 5: case 6: case 7: case 8: case 9: case 10: case 11:
                mEventTitle = mContext.getResources().getStringArray(R.array.welcome_message_variants)[0];
                mEventSubIcon = R.drawable.ic_quickspace_morning;
                break;

            case 12: case 13: case 14: case 15:
                mEventTitle = mContext.getResources().getStringArray(R.array.welcome_message_variants)[1];
                mEventSubIcon = R.drawable.ic_quickspace_noon;
                break;

            case 16: case 17: case 18: case 19:
                mEventTitle = mContext.getResources().getStringArray(R.array.welcome_message_variants)[2];
                mEventSubIcon = R.drawable.ic_quickspace_evening;
                break;
            
            case 20: case 21: case 22: case 23:
                mEventTitle = mContext.getResources().getStringArray(R.array.welcome_message_variants)[3];
                mEventSubIcon = R.drawable.ic_quickspace_evening;
                break;

            case 0: case 1: case 2: case 3: case 4:
                mEventTitle = mContext.getResources().getStringArray(R.array.welcome_message_variants)[4];
                mEventSubIcon = R.drawable.ic_quickspace_midnight;
                break;

            default:
                break;
      }
   }

    public String getTitle() {
        return mEventTitle;
    }

    public String getActionTitle() {
        return mEventTitleSub;
    }

    public OnClickListener getAction() {
        return mEventTitleSubAction;
    }

    public int getActionIcon() {
        return mEventSubIcon;
    }

    public int getLuckyNumber(int max) {
        return getLuckyNumber(0, max);
    }

    public int getLuckyNumber(int min, int max) {
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    public void onPause() {
        mRunning = false;
        unregisterPSAListener();
        mRegistered = false;
    }

    public void onResume() {
    	initQuickEvents();
    }
}
