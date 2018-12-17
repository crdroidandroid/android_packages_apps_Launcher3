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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.util.omni.OmniJawsClient;

import java.util.ArrayList;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver {

    public final ArrayList<OnDataListener> mListeners = new ArrayList();
    private static final String SETTING_WEATHER_LOCKSCREEN_UNIT = "weather_lockscreen_unit";
    private static final boolean DEBUG = false;
    private static final String TAG = "Launcher3:QuickspaceController";

    private Context mContext;
    private final Handler mHandler;
    private QuickEventsController mEventsController;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.PackageInfo mPackageInfo;
    private OmniJawsClient.WeatherInfo mWeatherInfo;

    private boolean mUseImperialUnit;

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mHandler = new Handler();
        mWeatherClient = new OmniJawsClient(context);
    }

    private void addWeatherProvider() {
        if (mWeatherClient.isOmniJawsEnabled()) {
            mWeatherClient.addObserver(this);
            updateWeather();
        }
    }

    private void addEventsController() {
        mEventsController = new QuickEventsController(mContext);
    }

    public void addListener(OnDataListener listener) {
        mListeners.add(listener);
        addEventsController();
        addWeatherProvider();
        listener.onDataUpdated();
    }

    public void removeListener(OnDataListener listener) {
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
        }
        mListeners.remove(listener);
    }

    public boolean isQuickEvent() {
        return mEventsController.isQuickEvent();
    }

    public QuickEventsController getEventController() {
        return mEventsController;
    }

    public boolean isWeatherAvailable() {
        return mWeatherInfo != null;
    }

    public Icon getWeatherIcon() {
        return Icon.createWithResource(mPackageInfo.packageName, mPackageInfo.resourceID);
    }

    public String getWeatherTemp() {
        String weatherTemp = mWeatherInfo.temp + mWeatherInfo.tempUnits;
        return weatherTemp;
    }

    public void onPause() {
        if (mEventsController != null) mEventsController.onPause();
    }

    public void onResume() {
        if (mEventsController != null) {
            mEventsController.onResume();
            notifyListeners();
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        if (DEBUG) Log.d(TAG, "queryAndUpdateWeather.isOmniJawsEnabled " + mWeatherClient.isOmniJawsEnabled());
        mWeatherInfo = null;
        updateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        mWeatherInfo = null;
        notifyListeners();
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        if (mWeatherClient.isOmniJawsEnabled()) {
            updateWeather();
        }
        notifyListeners();
    }

    private void updateWeather() {
        try {
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            if (mWeatherInfo != null) {
                Drawable conditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                mPackageInfo = mWeatherClient.getPackageInfo();
            }
            notifyListeners();
        } catch(Exception e) {
            // Do nothing
        }
    }

    public void notifyListeners() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : mListeners) {
                    list.onDataUpdated();
                }
            }
        });
    }
}
