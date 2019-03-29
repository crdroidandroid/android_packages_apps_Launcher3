/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.hidenprotect;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.AppFilter;
import com.android.launcher3.hidenprotect.db.HideAndProtectDatabaseHelper;

import java.util.HashSet;

@SuppressWarnings("unused")
public class HiddenAppsFilter implements AppFilter {
    private HideAndProtectDatabaseHelper mDbHelper;
    private final HashSet<String> mBlackList = new HashSet<>();

    public HiddenAppsFilter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null!");
        }

        mDbHelper = HideAndProtectDatabaseHelper.getInstance(context);
        mBlackList.add("com.google.android.googlequicksearchbox");
        mBlackList.add("com.google.android.apps.wallpaper");
        mBlackList.add("com.google.android.launcher");
    }

    @Override
    public boolean shouldShowApp(String packageName, Context context, boolean isWidgetPanel) {

        if (isWidgetPanel) {
            return !mBlackList.contains(packageName);
        }

        return !mDbHelper.isPackageHidden(packageName);
    }
}