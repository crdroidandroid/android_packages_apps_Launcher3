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
package com.android.launcher3.lineage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.launcher3.R;

public class LineageUtils {

    public static boolean isPackageEnabled(Context context, String pkgName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkgName, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isSystemApp(Context context, String pkgName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkgName, 0);
            return ai.isSystemApp();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isPackageWhitelisted(Context context, String pkgName) {
        String[] whiteListedPackages = context.getResources().getStringArray(
                com.android.internal.R.array.config_appLockAllowedSystemApps);
        for (int i = 0; i < whiteListedPackages.length; i++) {
            if (pkgName.equals(whiteListedPackages[i])) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPackageLockable(Context context, String pkgName) {
        return !isSystemApp(context, pkgName) || isPackageWhitelisted(context, pkgName);
    }
}
