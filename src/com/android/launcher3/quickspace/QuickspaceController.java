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
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.provider.Settings;

import com.android.internal.util.crdroid.weather.WeatherClient;
import com.android.internal.util.crdroid.weather.WeatherClient.WeatherInfo;
import com.android.internal.util.crdroid.weather.WeatherClient.WeatherObserver;

import java.util.ArrayList;

public class QuickspaceController implements WeatherObserver {

    public final ArrayList<OnDataListener> mListeners = new ArrayList();
    private static final String SETTING_WEATHER_LOCKSCREEN_UNIT = "weather_lockscreen_unit";

    private Context mContext;
    private final Handler mHandler;
    private QuickEventsController mEventsController;
    private WeatherClient mWeatherClient;
    private WeatherInfo mWeatherInfo;
    private WeatherSettingsObserver mWeatherSettingsObserver;

    private boolean mUseImperialUnit;

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mHandler = new Handler();
        mWeatherClient = new WeatherClient(context);
        mWeatherSettingsObserver = new WeatherSettingsObserver(
                mHandler, context.getContentResolver());
        mWeatherSettingsObserver.register();
        mWeatherSettingsObserver.updateLockscreenUnit();
    }

    private void addEventsController() {
        mEventsController = new QuickEventsController(mContext);
    }

    public void addListener(OnDataListener listener) {
        mListeners.add(listener);
        addEventsController();
        mWeatherClient.addObserver(this, true /*withQuery*/);
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
        return mWeatherInfo != null && mWeatherInfo.getStatus() == WeatherClient.WEATHER_UPDATE_SUCCESS;
    }

    public Icon getWeatherIcon() {
        return Icon.createWithResource(mContext, mWeatherInfo.getWeatherConditionImage());
    }

    public String getWeatherTemp() {
        int tempMetric = mWeatherInfo.getTemperature(true);
        int tempImperial = mWeatherInfo.getTemperature(false);
        String weatherTemp = mUseImperialUnit ?
                Integer.toString(tempImperial) + "°F" :
                Integer.toString(tempMetric) + "°C";
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
    public void onWeatherUpdated(WeatherInfo weatherInfo) {
        mWeatherInfo = weatherInfo;
        notifyListeners();
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

    private class WeatherSettingsObserver extends ContentObserver {

        private Handler mHandler;
        private ContentResolver mResolver;

        WeatherSettingsObserver(Handler handler, ContentResolver resolver) {
            super(handler);
            mHandler = handler;
            mResolver = resolver;
        }

        public void register() {
            mResolver.registerContentObserver(Settings.System.getUriFor(
                    SETTING_WEATHER_LOCKSCREEN_UNIT), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateLockscreenUnit();
        }

        public void updateLockscreenUnit() {
            mUseImperialUnit = Settings.System.getInt(mResolver, SETTING_WEATHER_LOCKSCREEN_UNIT, 0) != 0;
            notifyListeners();
        }
    }
}
