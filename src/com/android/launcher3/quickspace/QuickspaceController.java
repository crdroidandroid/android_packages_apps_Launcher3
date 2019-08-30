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
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.internal.util.crdroid.OmniJawsClient;

import com.android.launcher3.LauncherNotifications;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.List;

public class QuickspaceController implements NotificationListener.NotificationsChangedListener, OmniJawsClient.OmniJawsObserver {

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

    private AudioManager mAudioManager;
    private Metadata mMetadata = new Metadata();
    private RemoteController mRemoteController;
    private IAudioService mAudioService = null;
    private boolean mClientLost = true;
    private boolean mMediaActive = false;

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
        mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.registerRemoteController(mRemoteController);
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

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mMediaActive) {
            mMediaActive = active;
        }
        updateMediaInfo();
    }

    public void updateMediaInfo() {
        if (mEventsController != null) {
            mEventsController.setMediaInfo(mMetadata.trackTitle, mMetadata.trackArtist, mClientLost, mMediaActive);
            mEventsController.updateQuickEvents();
            notifyListeners();
        }
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey,
                                     NotificationKeyData notificationKey) {
        updateMediaInfo();
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
                                      NotificationKeyData notificationKey) {
        updateMediaInfo();
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        updateMediaInfo();
    }

    public void onPause() {
        if (mEventsController != null) mEventsController.onPause();
    }

    public void onResume() {
        if (mEventsController != null) {
            updateMediaInfo();
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

   private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mMediaActive = false;
                mClientLost = true;
            }
            updateMediaInfo();
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mMetadata.trackArtist = data.getString(MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    mMetadata.trackArtist);
            mClientLost = false;
            updateMediaInfo();
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;
        private String trackArtist;

         public void clear() {
            trackTitle = null;
            trackArtist = null;
        }
    }
}
