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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.crdroid.OmniJawsClient;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.MediaSessionManagerHelper;
import com.android.launcher3.util.MSMHProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver,
        MediaSessionManagerHelper.MediaMetadataListener {

    private static final String TAG = "Launcher3:QuickspaceController";

    private final List<OnDataListener> mListeners =
        Collections.synchronizedList(new ArrayList<>());
    private final Context mContext;
    private final Map<String, Integer> mConditionMap;
    private QuickEventsController mEventsController;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private Drawable mConditionImage;
    private boolean mOmniRegistered = false;

    private static final long PSA_UPDATE_DELAY_MS = 3 * 60 * 1000;

    private final Handler mHandler = MAIN_EXECUTOR.getHandler();
    private final Runnable mPsaRunnable;

    private final Runnable mOnDataUpdatedRunnable = new Runnable() {
        @Override
        public void run() {
            for (OnDataListener l : new ArrayList<>(mListeners)) {
                l.onDataUpdated();
            }
        }
    };

    private Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mWeatherClient == null) return;
                    mWeatherClient.queryWeather();
                    mWeatherInfo = mWeatherClient.getWeatherInfo();
                    if (mWeatherInfo != null) {
                        mConditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                    }
                } catch(Exception e) {
                    // Do nothing
                }
                notifyListeners();
            }
        };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mConditionMap = initializeConditionMap();
        mEventsController = new QuickEventsController(context);

        mPsaRunnable = new Runnable() {
            @Override
            public void run() {
                if (mEventsController != null) {
                    mEventsController.updatePsonality();
                    notifyListeners();
                }
                mHandler.postDelayed(this, PSA_UPDATE_DELAY_MS);
            }
        };
    }

    private void addWeatherProvider() {
        if (!Utilities.isQuickspaceWeather(mContext)) return;
        if (mWeatherClient == null) mWeatherClient = new OmniJawsClient(mContext);
        if (!mOmniRegistered) {
            mWeatherClient.addObserver(this);
            mOmniRegistered = true;
        }
        queryAndUpdateWeather();
    }

    public void addListener(OnDataListener listener) {
        if (listener == null) return;
        boolean wasEmpty = mListeners.isEmpty();
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
        if (wasEmpty) {
            addWeatherProvider();
            registerMediaController();
            mEventsController.initQuickEvents();
            mHandler.post(mPsaRunnable);
        }
        listener.onDataUpdated();
    }

    private void removeOmniIfRegistered() {
        if (mOmniRegistered && mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
            mOmniRegistered = false;
        }
        mWeatherClient = null;
        mWeatherInfo = null;
        mConditionImage = null;
    }

    public void removeListener(OnDataListener listener) {
        if (listener == null) return;
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            removeOmniIfRegistered();
            unregisterMediaController();
            mHandler.removeCallbacks(mPsaRunnable);
            mHandler.removeCallbacks(mWeatherRunnable);
            mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        }
    }

    public boolean isQuickEvent() {
        return mEventsController.isQuickEvent();
    }

    public QuickEventsController getEventController() {
        return mEventsController;
    }

    public boolean isWeatherAvailable() {
        return mWeatherClient != null && mWeatherClient.isOmniJawsEnabled();
    }

    public Drawable getWeatherIcon() {
        return mConditionImage;
    }

    public String getWeatherTemp() {
        if (mWeatherInfo == null) return null;

        boolean shouldShowCity = Utilities.QuickSpaceShowCity(mContext);
        boolean showWeatherText = Utilities.QuickSpaceShowWeatherText(mContext);

        StringBuilder weatherTemp = new StringBuilder();
        if (shouldShowCity) {
            weatherTemp.append(mWeatherInfo.city).append(" ");
        }
        weatherTemp.append(mWeatherInfo.temp)
                   .append(mWeatherInfo.tempUnits);

        if (showWeatherText) {
            weatherTemp.append(" • ").append(getConditionText(mWeatherInfo.condition));
        }

        return weatherTemp.toString();
    }

    private String getConditionText(String input) {
        if (input == null || input.isEmpty()) return "";

        Locale locale = mContext.getResources().getConfiguration().getLocales().get(0);
        boolean isEnglish = locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("en");
        String lowerCaseInput = input.toLowerCase();

        if (!isEnglish) {
            for (Map.Entry<String, Integer> entry : mConditionMap.entrySet()) {
                if (lowerCaseInput.contains(entry.getKey())) {
                    return mContext.getResources().getString(entry.getValue());
                }
            }
        }
        return capitalizeWords(lowerCaseInput);
    }

    private Map<String, Integer> initializeConditionMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("clouds", R.string.quick_event_weather_clouds);
        map.put("rain", R.string.quick_event_weather_rain);
        map.put("clear", R.string.quick_event_weather_clear);
        map.put("storm", R.string.quick_event_weather_storm);
        map.put("snow", R.string.quick_event_weather_snow);
        map.put("wind", R.string.quick_event_weather_wind);
        map.put("mist", R.string.quick_event_weather_mist);
        return map;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] words = input.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        return capitalized.toString().trim();
    }

    public void onPause() {
        unregisterMediaController();
    }

    public void onResume() {
        registerMediaController();
        updateMediaController();
        notifyListeners();
    }

    public void onDestroy() {
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        Log.d(TAG, "weatherError " + errorReason);
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            notifyListeners();
        }
    }

    @Override
    public void updateSettings() {
        Log.i(TAG, "updateSettings");
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        mHandler.post(mWeatherRunnable);
    }

    public void notifyListeners() {
        mHandler.post(mOnDataUpdatedRunnable);
    }

    private void registerMediaController() {
        MSMHProxy.INSTANCE(mContext).addMediaMetadataListener(this);
    }

    private void unregisterMediaController() {
        MSMHProxy.INSTANCE(mContext).removeMediaMetadataListener(this);
    }

    private boolean updateMediaController() {
        if (!Utilities.isQuickspaceNowPlaying(mContext)) {
            return false;
        }
        MediaMetadata mediaMetadata = MSMHProxy.INSTANCE(mContext).getCurrentMediaMetadata();
        boolean isPlaying = MSMHProxy.INSTANCE(mContext).isMediaPlaying();
        String trackArtist = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "";
        String trackTitle = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
        mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying);
        mEventsController.updateQuickEvents();
        return true;
    }

    @Override
    public void onMediaMetadataChanged() {
        if (updateMediaController()) notifyListeners();
    }

    @Override
    public void onPlaybackStateChanged() {
        if (updateMediaController()) notifyListeners();
    }
}
