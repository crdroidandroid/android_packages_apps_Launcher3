/*
 * Copyright (C) 2021-2025 crDroid Android Project
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
import android.graphics.Bitmap;
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

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.MediaSessionManagerHelper;
import com.android.launcher3.util.MSMHProxy;

import io.chaldeaprjkt.seraphixgoogle.SeraphixDataProvider;
import io.chaldeaprjkt.seraphixgoogle.DataProviderListener;

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
    private boolean mMediaRegistered = false;

    private static final long PSA_UPDATE_DELAY_MS = 3 * 60 * 1000;

    private final Handler mHandler = MAIN_EXECUTOR.getHandler();

    private enum WeatherProvider { OMNIJAWS, SERAPHIX }
    private WeatherProvider mProvider;

    private SeraphixDataProvider mSeraphix;
    private String mSeraphixText;
    private Icon mSeraphixIcon;
    private String mLastText;
    private int mLastBmpHash;

    private final Runnable mOnDataUpdatedRunnable = new Runnable() {
            @Override
            public void run() {
                for (OnDataListener list : new ArrayList<>(mListeners)) {
                    list.onDataUpdated();
                }
            }
        };

    private Runnable mWeatherRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mWeatherClient == null) return;
                    mWeatherClient.queryWeather(mContext);
                    mWeatherInfo = mWeatherClient.getWeatherInfo();
                    if (mWeatherInfo != null) {
                        mConditionImage = mWeatherClient.getWeatherConditionImage(mContext, mWeatherInfo.conditionCode);
                    }
                    notifyListeners();
                } catch(Exception e) {
                    // Do nothing
                }
            }
        };

    private Runnable mPsaRunnable = new Runnable() {
            @Override
            public void run() {
                mHandler.removeCallbacks(this);
                if (mEventsController == null) return;
                mEventsController.updatePsonality();
                mHandler.postDelayed(this, PSA_UPDATE_DELAY_MS);
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
    }

    private void decideWeatherProvider() {
        String pref = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_PROVIDER.get(mContext);
        WeatherProvider target = WeatherProvider.SERAPHIX;
        if ("seraphix".equals(pref)) {
            target = WeatherProvider.SERAPHIX;
        } else if ("auto".equals(pref)) {
            // Try seraphix first; if bind fails, fall back to OmniJaws
            if (tryBindSeraphix(true)) {
                target = WeatherProvider.SERAPHIX;
            } else {
                target = WeatherProvider.OMNIJAWS;
            }
        } else if ("omnijaws".equals(pref)) {
            target = WeatherProvider.OMNIJAWS;
        }
        switchProvider(target);
    }

    private void switchProvider(WeatherProvider target) {
        if (mProvider == target) {
            // Ensure the chosen provider is actually set up
            if (target == WeatherProvider.SERAPHIX) {
                tryBindSeraphix(false);
            } else {
                addOmniJawsIfEnabled();
            }
            return;
        }

        // Tear down old
        if (mProvider == WeatherProvider.SERAPHIX) {
            unbindSeraphix();
        } else if (mProvider == WeatherProvider.OMNIJAWS) {
            removeOmniIfRegistered();
        }

        mProvider = target;

        // Bring up new
        if (mProvider == WeatherProvider.SERAPHIX) {
            if (!tryBindSeraphix(false)) {
                // fallback if bind fails at runtime
                mProvider = WeatherProvider.OMNIJAWS;
                addOmniJawsIfEnabled();
            }
        } else {
            addOmniJawsIfEnabled();
        }

        notifyListeners();
    }

    private void addOmniJawsIfEnabled() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return;
        if (mWeatherClient == null) mWeatherClient = OmniJawsClient.get();
        if (!mOmniRegistered) {
            mWeatherClient.addObserver(mContext, this);
            mOmniRegistered = true;
        }
        queryAndUpdateWeather();
    }

    private boolean tryBindSeraphix(boolean silent) {
        try {
            if (mSeraphix == null) {
                mSeraphix = new SeraphixDataProvider(mContext, 1022,
                    LauncherPrefs.SERAPHIX_HOLDER_ID.get(mContext));
                mSeraphix.setOnDataUpdated(mSeraphixListener);
            }
            mSeraphix.bind(id -> { 
                LauncherPrefs.get(mContext).put(LauncherPrefs.SERAPHIX_HOLDER_ID, id);
            });
            return true;
        } catch (Throwable t) {
            if (!silent) Log.w(TAG, "Seraphix bind failed, falling back", t);
            unbindSeraphix();
            return false;
        }
    }

    private void unbindSeraphix() {
        try {
            if (mSeraphix != null) {
                mSeraphix.setOnDataUpdated(null);
                mSeraphix.unbind();
            }
        } catch (Throwable ignored) {}
        mSeraphix = null;
        mSeraphixText = null;
        mSeraphixIcon = null;
    }

    private final DataProviderListener mSeraphixListener = card -> {
        try {
            updateWeatherData(card.getText(), card.getImage());
        } catch (Exception e) {
            Log.e(TAG, "Seraphix update error", e);
        }
    };

    private void updateWeatherData(String text, Bitmap image) {
        int hash = (image == null) ? 0 : image.getGenerationId();
        if (TextUtils.equals(text, mSeraphixText) && hash == mLastBmpHash) {
            return;
        }
        mLastBmpHash = hash;
        mSeraphixText = text;
        mSeraphixIcon = image == null ? null : Icon.createWithBitmap(image);
        notifyListeners();
    }

    public void addListener(OnDataListener listener) {
        if (listener == null) return;
        boolean wasEmpty = mListeners.isEmpty();
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
        if (wasEmpty) {
            decideWeatherProvider();
            registerMediaController();
            mEventsController.initQuickEvents();
            updatePSAevent();
        }
        listener.onDataUpdated();
    }

    private void removeOmniIfRegistered() {
        if (mOmniRegistered && mWeatherClient != null) {
            mWeatherClient.removeObserver(mContext, this);
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
            if (mProvider == WeatherProvider.OMNIJAWS) {
                removeOmniIfRegistered();
            } else {
                unbindSeraphix();
            }
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
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return false;
        if (mProvider == WeatherProvider.SERAPHIX) {
            return !TextUtils.isEmpty(mSeraphixText) || mSeraphixIcon != null;
        } else {
            return mWeatherClient != null && mWeatherClient.isOmniJawsEnabled(mContext);
        }
    }

    public Drawable getWeatherIcon() {
        if (mProvider == WeatherProvider.SERAPHIX) {
            return mSeraphixIcon != null ? mSeraphixIcon.loadDrawable(mContext) : null;
        } else {
            return mConditionImage;
        }
    }

    public String getWeatherTemp() {
        if (mProvider == WeatherProvider.SERAPHIX) {
            return mSeraphixText;
        } else {
            if (mWeatherInfo == null) return null;

            boolean shouldShowCity = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY.get(mContext);
            boolean showWeatherText = LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT.get(mContext);

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
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        if (mProvider == WeatherProvider.SERAPHIX && mSeraphix != null) {
            mSeraphix.pauseListening();
        }
    }

    public void onResume() {
        registerMediaController();
        updateMediaController();
        decideWeatherProvider();
        if (mProvider == WeatherProvider.SERAPHIX && mSeraphix != null) {
            mSeraphix.resumeListening();
        }
        updatePSAevent();
        notifyListeners();
    }

    public void onDestroy() {
        unregisterMediaController();
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
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

    private void updatePSAevent() {
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.post(mPsaRunnable);
    }

    private void queryAndUpdateWeather() {
        mHandler.removeCallbacks(mWeatherRunnable);
        mHandler.post(mWeatherRunnable);
    }

    public void notifyListeners() {
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        mHandler.post(mOnDataUpdatedRunnable);
    }

    private void registerMediaController() {
        if (mMediaRegistered) return;
        MSMHProxy.INSTANCE(mContext).addMediaMetadataListener(this);
        mMediaRegistered = true;
    }

    private void unregisterMediaController() {
        if (!mMediaRegistered) return;
        MSMHProxy.INSTANCE(mContext).removeMediaMetadataListener(this);
        mMediaRegistered = false;
    }

    private boolean updateMediaController() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
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
