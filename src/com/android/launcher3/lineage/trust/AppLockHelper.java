/*
 * Copyright (C) 2019 The LineageOS Project
 * Copyright (C) 2023 AlphaDroid
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
package com.android.launcher3.lineage.trust;

import android.app.AppLockData;
import android.app.AppLockManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class AppLockHelper {

    private AppLockManager mAppLockManager;

    @Nullable
    private static AppLockHelper sSingleton;

    private AppLockHelper(@NonNull Context context) {
        mAppLockManager = context.getSystemService(AppLockManager.class);
    }

    public static synchronized AppLockHelper getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            sSingleton = new AppLockHelper(context);
        }
        return sSingleton;
    }

    public void addProtectedApp(@NonNull String packageName) {
        mAppLockManager.addPackage(packageName);
    }

    public void removeProtectedApp(@NonNull String packageName) {
        mAppLockManager.removePackage(packageName);
    }

    public boolean isProtected(@NonNull String packageName) {

        List<AppLockData> appList = mAppLockManager.getPackageData();
        for (int i = 0; i < appList.size(); i++) {
            if (packageName.equals(appList.get(i).getPackageName())) {
                return true;
            }
        }

        /*for (AppLockData data : mAppLockManager.getPackageData()) {
            if (package.equals(data.getPackageName())) {
                return true;
            }
        }*/

        return false;
    }
}
