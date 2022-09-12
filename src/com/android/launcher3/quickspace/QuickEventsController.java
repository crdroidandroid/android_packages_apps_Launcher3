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

public class QuickEventsController {

    private Context mContext;

    private String mEventTitleSub;
    private String mGreetingsExt;
    private OnClickListener mEventTitleSubAction = null;
    private int mEventSubIcon;

    private boolean mRunning = true;
    private boolean mRegistered = false;

    // PSA + Personality
    private String[] mPSAMorningStr;
    private String[] mPSAEvenStr;
    private String[] mPSAAfterNoonStr;
    private String[] mPSAMidniteStr;
    private String[] mPSARandomStr;
    private String[] mPSAEarlyEvenStr;
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
        registerPSAListener();
        updateQuickEvents();
    }

    private void registerPSAListener() {
        if (mRegistered) return;
        mRegistered = true;
        IntentFilter psonalityIntent = new IntentFilter();
        psonalityIntent.addAction(Intent.ACTION_TIME_TICK);
        psonalityIntent.addAction(Intent.ACTION_TIME_CHANGED);
        psonalityIntent.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        mContext.registerReceiver(mPSAListener, psonalityIntent);
    }

    private void unregisterPSAListener() {
       if (!mRegistered) return;
       mRegistered = false;
        mContext.unregisterReceiver(mPSAListener);
    }

    public void updateQuickEvents() {
        if (!mRunning) return;
        
        mEventTitleSub = mContext.getResources().getStringArray(R.array.quickspace_psa_random)[getLuckyNumber(0,22)];
        mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_general);
        mEventSubIcon = R.drawable.ic_quickspace_info;

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

        mPSAMorningStr = mContext.getResources().getStringArray(R.array.quickspace_psa_morning);
        mPSAEvenStr = mContext.getResources().getStringArray(R.array.quickspace_psa_evening);
        mPSAEarlyEvenStr = mContext.getResources().getStringArray(R.array.quickspace_psa_early_evening);
        mPSAMidniteStr = mContext.getResources().getStringArray(R.array.quickspace_psa_midnight);
        mPSAAfterNoonStr = mContext.getResources().getStringArray(R.array.quickspace_psa_noon);
        mPSARandomStr = mContext.getResources().getStringArray(R.array.quickspace_psa_random);
        int psaLength;

        // Clean the onClick event to avoid any weird behavior
        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                // haha yes
            }
        };

        switch (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            case 5: case 6: case 7: case 8: case 9: case 10:
                psaLength = mPSAMorningStr.length - 1;
                mEventTitleSub = mPSAMorningStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_morning);
                break;

            case 21: case 22: case 23: case 0: 
                psaLength = mPSAEvenStr.length - 1;
                mEventTitleSub = mPSAEvenStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_night);
                break;
                
            case 18: case 19: case 20:
                psaLength = mPSAEarlyEvenStr.length - 1;
                mEventTitleSub = mPSAEarlyEvenStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_evening);
                break;

             case 15: case 16: case 17:
                psaLength = mPSAAfterNoonStr.length - 1;
                mEventTitleSub = mPSAAfterNoonStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_afternoon);
                break;

            case 1: case 2: case 3: case 4:
                psaLength = mPSAMidniteStr.length - 1;
                mEventTitleSub = mPSAMidniteStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_midnight);
                break;
                
            case 11: case 12: case 13: case 14: 
                psaLength = mPSARandomStr.length - 1;
                mEventTitleSub = mPSARandomStr[getLuckyNumber(0, psaLength)];
                mGreetingsExt = mContext.getResources().getString(R.string.quickspace_grt_general);
                break;

            default:
                break;
      }
   }

    public String getActionTitle() {
        return mEventTitleSub;
    }

    public String getGreetings() {
        return mGreetingsExt;
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
    }

    public void onResume() {
        mRunning = true;
        registerPSAListener();
    }
}
