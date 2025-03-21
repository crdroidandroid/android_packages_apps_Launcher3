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
import java.util.List;

public class QuickspaceController implements OmniJawsClient.OmniJawsObserver, MediaSessionManagerHelper.MediaMetadataListener {

    public final ArrayList<OnDataListener> mListeners = new ArrayList();
    private static final String SETTING_WEATHER_LOCKSCREEN_UNIT = "weather_lockscreen_unit";
    private static final boolean DEBUG = false;
    private static final String TAG = "Launcher3:QuickspaceController";

    private final Context mContext;
    private QuickEventsController mEventsController;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private Drawable mConditionImage;

    private Runnable mOnDataUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : mListeners) {
                    list.onDataUpdated();
                }
            }
        };

    private Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    mWeatherClient.queryWeather();
                    mWeatherInfo = mWeatherClient.getWeatherInfo();
                    if (mWeatherInfo != null) {
                        mConditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                    }
                    notifyListeners();
                } catch(Exception e) {
                    // Do nothing
                }
            }
        };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mEventsController = new QuickEventsController(context);
        mWeatherClient = new OmniJawsClient(context);
    }

    private void addWeatherProvider() {
        if (!Utilities.isQuickspaceWeather(mContext)) return;
        mWeatherClient.addObserver(this);
        queryAndUpdateWeather();
    }

    public void addListener(OnDataListener listener) {
        mListeners.add(listener);
        addWeatherProvider();
        MSMHProxy.INSTANCE(mContext).addMediaMetadataListener(this);
        listener.onDataUpdated();
    }

    private void removeListener(OnDataListener listener) {
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
        return mWeatherClient != null && mWeatherClient.isOmniJawsEnabled();
    }

    public Drawable getWeatherIcon() {
        return mConditionImage;
    }

    public String getWeatherTemp() {
        boolean shouldShowCity = Utilities.QuickSpaceShowCity(mContext);
        boolean showWeatherText = Utilities.QuickSpaceShowWeatherText(mContext);
        if (mWeatherInfo != null) {
            String weatherTemp = (shouldShowCity ? mWeatherInfo.city : "") + " " + mWeatherInfo.temp +
                    mWeatherInfo.tempUnits + 
                    (showWeatherText ? " · " + capitalizeWords(mWeatherInfo.condition) : "");
            return weatherTemp;
        }
        return null;
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
        cancelListeners();
    }

    public void onResume() {
        mEventsController.onResume();
        updateMediaController();
        notifyListeners();
    }

    private void cancelListeners() {
        if (mEventsController != null) {
            mEventsController.onPause();
        }
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
        unregisterMediaController();
    }

    public void onDestroy() {
        cancelListeners();
        mWeatherClient = null;
        mWeatherInfo = null;
        mConditionImage = null;
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
        MAIN_EXECUTOR.execute(mWeatherRunnable);
    }

    public void notifyListeners() {
        MAIN_EXECUTOR
            .getHandler()
            .post(mOnDataUpdatedRunnable);
    }

    private void unregisterMediaController() {
        MSMHProxy.INSTANCE(mContext).removeMediaMetadataListener(this);
    }

    private void updateMediaController() {
        if (!Utilities.isQuickspaceNowPlaying(mContext)) {
            unregisterMediaController();
            return;
        }
        MediaMetadata mediaMetadata = MSMHProxy.INSTANCE(mContext).getCurrentMediaMetadata();
        boolean isPlaying = MSMHProxy.INSTANCE(mContext).isMediaPlaying();
        String trackArtist = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "";
        String trackTitle = isPlaying && mediaMetadata != null ? mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
        mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying);
        mEventsController.updateQuickEvents();
        notifyListeners();
    }
    
    @Override
    public void onMediaMetadataChanged() {
        updateMediaController();
    }

    @Override
    public void onPlaybackStateChanged() {
        updateMediaController();
    }
}
