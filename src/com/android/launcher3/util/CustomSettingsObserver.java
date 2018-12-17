/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

public interface CustomSettingsObserver {

    /**
     * Registers the content observer to call {@link #onSettingChanged(int)} when any of the
     * passed settings change. The value passed to onSettingChanged() is based on the key setting.
     */
    void register(String keySetting, String ... dependentSettings);
    void unregister();
    void onSettingChanged(int keySettingInt);

    abstract class System extends ContentObserver implements CustomSettingsObserver {
        private ContentResolver mResolver;
        private String mKeySetting;

        public System(ContentResolver resolver) {
            super(new Handler());
            mResolver = resolver;
        }

        @Override
        public void register(String keySetting, String ... dependentSettings) {
            mKeySetting = keySetting;
            mResolver.registerContentObserver(
                    Settings.System.getUriFor(mKeySetting), false, this);
            for (String setting : dependentSettings) {
                mResolver.registerContentObserver(
                        Settings.System.getUriFor(setting), false, this);
            }
        }

        @Override
        public void unregister() {
            mResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            onSettingChanged(Settings.System.getIntForUser(mResolver, mKeySetting, 0, UserHandle.USER_CURRENT));
        }

        public int getSettingInt() {
            return Settings.System.getIntForUser(mResolver, mKeySetting, 0, UserHandle.USER_CURRENT);
        }
    }
}
