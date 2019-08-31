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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import com.android.launcher3.LauncherNotifications;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.PackageUserKey;

import com.android.internal.util.crdroid.weather.WeatherClient;
import com.android.internal.util.crdroid.weather.WeatherClient.WeatherInfo;
import com.android.internal.util.crdroid.weather.WeatherClient.WeatherObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuickspaceController extends MediaController.Callback implements NotificationListener.NotificationsChangedListener,  WeatherObserver, 
        MediaSessionManager.OnActiveSessionsChangedListener {

    public final ArrayList<OnDataListener> mListeners = new ArrayList();
    private static final String SETTING_WEATHER_LOCKSCREEN_UNIT = "weather_lockscreen_unit";
    private final List<NotificationKeyData> mNotifications = new ArrayList<>();
    private final List<StatusBarNotification> mSbn = new ArrayList<>();
    private final ComponentName mComponent;
    private final MediaSessionManager mManager;
    private List<MediaController> mControllers = Collections.emptyList();

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
        mComponent = new ComponentName(context, NotificationListener.class);
        mManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mWeatherSettingsObserver = new WeatherSettingsObserver(
                mHandler, context.getContentResolver());
        mWeatherSettingsObserver.register();
        mWeatherSettingsObserver.updateLockscreenUnit();
    }

    private void addEventsController() {
        onActiveSessionsChanged(null); // Bind all current controllers.
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
        if (mEventsController != null) {
            mEventsController.destroy();
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
        if (mEventsController != null) {
            mManager.removeOnActiveSessionsChangedListener(this);
            onActiveSessionsChanged(Collections.emptyList()); // Unbind all previous controllers.
            mEventsController.onPause();
        }
    }

    public void onResume() {
        if (mEventsController != null) {
            try {
                mManager.addOnActiveSessionsChangedListener(this, mComponent);
            } catch (SecurityException ignored) {
            }
            onActiveSessionsChanged(null); // Bind all current controllers.
            mEventsController.onResume();
            notifyListeners();
        }
    }

    @Override
    public void onWeatherUpdated(WeatherInfo weatherInfo) {
        mWeatherInfo = weatherInfo;
        notifyListeners();
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey,
                                     NotificationKeyData notificationKey,
                                     boolean shouldBeFilteredOut) {
        if (!shouldBeFilteredOut) {
            mNotifications.remove(notificationKey);
            mNotifications.add(notificationKey);
            onNotificationsChanged();
        }
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
                                      NotificationKeyData notificationKey) {
        if (mNotifications.remove(notificationKey)) {
            onNotificationsChanged();
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        mNotifications.clear();
        for (int i = activeNotifications.size() - 1; i >= 0; i--) {
            mNotifications.add(NotificationKeyData.fromNotification(activeNotifications.get(i)));
        }
        onNotificationsChanged();
    }

    public void onNotificationsChanged() {
        mSbn.clear();
        if (!mNotifications.isEmpty()) {
            NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
            if (notificationListener != null) {
                mSbn.addAll(notificationListener.getNotificationsForKeys(mNotifications));
            }
        }
        onActiveSessionsChanged(null); // Bind all current controllers.
        if (mEventsController != null) {
            mEventsController.updateQuickEvents();
            notifyListeners();
        }
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        if (controllers == null) {
            try {
                controllers = mManager.getActiveSessions(mComponent);
            } catch (SecurityException ignored) {
                controllers = Collections.emptyList();
            }
        }
        updateControllers(controllers);
        notifyListeners();
    }

    private void updateControllers(List<MediaController> controllers) {
        for (MediaController mc : mControllers) {
            mc.unregisterCallback(this);
        }
        for (MediaController mc : controllers) {
            mc.registerCallback(this);
        }
        mControllers = controllers;
        if (mEventsController != null) {
            mEventsController.updateQuickEvents();
            notifyListeners();
        }
    }

    @Override
    public void onMetadataChanged(MediaMetadata metadata) {
        mEventsController.updateQuickEvents();
        notifyListeners();
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        mEventsController.initQuickEvents();
        notifyListeners();
     }

    @Override
    public void onSessionDestroyed() {
        mEventsController.initQuickEvents();
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
