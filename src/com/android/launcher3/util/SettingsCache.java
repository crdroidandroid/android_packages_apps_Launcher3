/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.util;

import static android.provider.Settings.System.ACCELEROMETER_ROTATION;

import static com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.concurrent.annotations.LightweightBackground;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;

import lineageos.providers.LineageSettings;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * ContentObserver over Settings keys that also has a caching layer.
 * Consumers can register for callbacks via {@link #register(Uri, OnChangeListener)} and
 * {@link #unregister(Uri, OnChangeListener)} methods.
 *
 * This can be used as a normal cache without any listeners as well via the
 * {@link #getValue} and {@link #onChange(boolean, Uri)} to update (and subsequently call get)
 *
 * The cache will be invalidated/updated through the normal
 * {@link ContentObserver#onChange(boolean)} calls
 *
 * Cache will also be updated if a key queried is missing (even if it has no listeners registered).
 *
 * <p>Both boolean and integer views over the same key are supported. Internally the raw integer
 * value is cached; the boolean view is simply {@code rawValue == 1}. Use {@link #getValue} /
 * {@link #getListenableRef} for on/off settings and {@link #getIntValue} /
 * {@link #getIntListenableRef} for multi-value (0, 1, 2, ...) settings.
 */
@ThreadSafe
@LauncherAppSingleton
public class SettingsCache extends ContentObserver {

    /** Hidden field Settings.Secure.NOTIFICATION_BADGING */
    public static final Uri NOTIFICATION_BADGING_URI =
            Settings.Secure.getUriFor("notification_badging");
    /** Hidden field Settings.Secure.ONE_HANDED_MODE_ENABLED */
    public static final String ONE_HANDED_ENABLED = "one_handed_mode_enabled";
    /** Hidden field Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED */
    public static final String ONE_HANDED_SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED =
            "swipe_bottom_to_notification_enabled";
    /** Hidden field Settings.Secure.HIDE_PRIVATESPACE_ENTRY_POINT */
    public static final Uri PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI =
            Settings.Secure.getUriFor("hide_privatespace_entry_point");
    public static final Uri ROTATION_SETTING_URI =
            Settings.System.getUriFor(ACCELEROMETER_ROTATION);
    /** Hidden field {@link Settings.System#TOUCHPAD_NATURAL_SCROLLING}. */
    public static final Uri TOUCHPAD_NATURAL_SCROLLING = Settings.System.getUriFor(
            "touchpad_natural_scrolling");

    private static final String SYSTEM_URI_PREFIX = Settings.System.CONTENT_URI.toString();
    private static final String GLOBAL_URI_PREFIX = Settings.Global.CONTENT_URI.toString();

    private static final String LINEAGE_SYSTEM_URI_PREFIX =
            LineageSettings.System.CONTENT_URI.toString();
    private static final String LINEAGE_SECURE_URI_PREFIX =
            LineageSettings.Secure.CONTENT_URI.toString();

    private final Function<Uri, MutableListenableRef<Boolean>> mListenerMapper = uri -> {
        registerUriAsync(uri);
        boolean value = false;
        try {
            value = getValue(uri);
        } catch (SecurityException e) {
            // A SecurityException is thrown when the value is not readable yet
            Log.e("SettingsCache", "", e);
        }
        return new MutableListenableRef<>(value);
    };

    private final Function<Uri, MutableListenableRef<Integer>> mIntListenerMapper = uri -> {
        registerUriAsync(uri);
        int value = 0;
        try {
            value = getIntValue(uri);
        } catch (SecurityException e) {
            // A SecurityException is thrown when the value is not readable yet
            Log.e("SettingsCache", "", e);
        }
        return new MutableListenableRef<>(value);
    };

    /**
     * Caches the last seen raw integer value for registered keys. The boolean view of a key is
     * derived from this as {@code value == 1}.
     */
    private final Map<Uri, Integer> mKeyCache = new ConcurrentHashMap<>();
    private final Map<Uri, MutableListenableRef<Boolean>> mListenerMap = new ConcurrentHashMap<>();
    private final Map<Uri, MutableListenableRef<Integer>> mIntListenerMap =
            new ConcurrentHashMap<>();
    private final Set<Uri> mUrisEnabledByDefault;
    protected final ContentResolver mResolver;
    private final Executor mLightweightBackgroundExecutor;

    /**
     * Singleton instance
     */
    public static final DaggerSingletonObject<SettingsCache> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getSettingsCache);

    @Inject
    SettingsCache(@ApplicationContext Context context,
            @Named("SETTINGS_ENABLED_BY_DEFAULT") Set<Uri> urisEnabledByDefault,
            DaggerSingletonTracker tracker,
            @LightweightBackground(priority = UI) LooperExecutor lightweightBgLooperExecutor) {
        super(new Handler(lightweightBgLooperExecutor.getLooper()));
        mResolver = context.getContentResolver();
        mUrisEnabledByDefault = urisEnabledByDefault;
        mLightweightBackgroundExecutor = lightweightBgLooperExecutor;
        tracker.addCloseable(() ->
                mLightweightBackgroundExecutor.execute(
                        () -> mResolver.unregisterContentObserver(this)));
    }

    @WorkerThread
    @Override
    public void onChange(boolean selfChange, Uri uri) {
        // We use default of 1, but if we're getting an onChange call, can assume a non-default
        // value will exist. Cache the raw int and fan out to whichever listeners are registered
        // for this uri (boolean and/or integer).
        int newVal = computeNewValue(uri);
        mKeyCache.put(uri, newVal);

        MutableListenableStream<Boolean> boolListeners = mListenerMap.get(uri);
        if (boolListeners != null) {
            boolListeners.dispatchValue(newVal == 1);
        }
        MutableListenableStream<Integer> intListeners = mIntListenerMap.get(uri);
        if (intListeners != null) {
            intListeners.dispatchValue(newVal);
        }
    }

    /**
     * Returns the boolean value for this key from the cache (i.e. {@code rawValue == 1}). If not in
     * cache, will call {@link #computeNewValue(Uri)} to fetch.
     */
    @AnyThread
    public boolean getValue(Uri keySetting) {
        return getIntValue(keySetting) == 1;
    }

    /**
     * Returns the raw integer value for this key from the cache. If not in cache, will call
     * {@link #computeNewValue(Uri)} to fetch. Missing keys fall back to their default value
     * (1 for keys in {@code SETTINGS_ENABLED_BY_DEFAULT}, otherwise 0).
     */
    @AnyThread
    public int getIntValue(Uri keySetting) {
        return mKeyCache.computeIfAbsent(keySetting, this::computeNewValue);
    }

    private void registerUriAsync(Uri uri) {
        mLightweightBackgroundExecutor.execute(
                () -> mResolver.registerContentObserver(uri, false, this));
    }

    /**
     * Does not de-dupe if you add same listeners for the same key multiple times.
     * Unregister once complete using {@link #unregister(Uri, OnChangeListener)}
     *
     * Note that the returned {@link ListenableRef} will receive new value on main thread.
     */
    @AnyThread
    public ListenableRef<Boolean> getListenableRef(Uri uri) {
        return mListenerMap.computeIfAbsent(uri, mListenerMapper);
    }

    /**
     * Integer counterpart to {@link #getListenableRef(Uri)}. Emits the raw integer value of the
     * key (0, 1, 2, ...) whenever it changes.
     *
     * Does not de-dupe if you add same listeners for the same key multiple times.
     * Note that the returned {@link ListenableRef} will receive new value on main thread.
     */
    @AnyThread
    public ListenableRef<Integer> getIntListenableRef(Uri uri) {
        return mIntListenerMap.computeIfAbsent(uri, mIntListenerMapper);
    }

    private int computeNewValue(Uri keyUri) {
        String key = keyUri.getLastPathSegment();
        int defaultValue = mUrisEnabledByDefault.contains(keyUri) ? 1 : 0;
        String uriString = keyUri.toString();
        int newVal;
        if (uriString.startsWith(SYSTEM_URI_PREFIX)) {
            newVal = Settings.System.getInt(mResolver, key, defaultValue);
        } else if (uriString.startsWith(GLOBAL_URI_PREFIX)) {
            newVal = Settings.Global.getInt(mResolver, key, defaultValue);
        } else if (uriString.startsWith(LINEAGE_SYSTEM_URI_PREFIX)) {
            newVal = LineageSettings.System.getInt(mResolver, key, defaultValue);
        } else if (uriString.startsWith(LINEAGE_SECURE_URI_PREFIX)) {
            newVal = LineageSettings.Secure.getInt(mResolver, key, defaultValue);
        } else { // SETTING_SECURE
            newVal = Settings.Secure.getInt(mResolver, key, defaultValue);
        }

        return newVal;
    }
}
