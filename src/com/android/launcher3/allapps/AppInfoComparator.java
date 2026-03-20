/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.LabelComparator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A comparator to arrange items based on user profiles.
 */
public class AppInfoComparator implements Comparator<AppInfo> {

    private final UserCache mUserManager;
    private final UserHandle mMyUser;
    private final LabelComparator mLabelComparator;

    private final Context mContext;
    private Map<String, Long> mUsageStats = null;
    private long mLastUsageUpdateTime = 0;

    public AppInfoComparator(Context context) {
        mContext = context;
        mUserManager = UserCache.INSTANCE.get(context);
        mMyUser = Process.myUserHandle();
        mLabelComparator = new LabelComparator();
    }

    private Map<String, Long> getUsageStats() {
        long now = System.currentTimeMillis();
        if (mUsageStats == null || now - mLastUsageUpdateTime > 60000) {
            mUsageStats = new HashMap<>();
            UsageStatsManager usm = (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long endtime = now;
                long starttime = endtime - 1000L * 60 * 60 * 24 * 30; // 30 days
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, starttime, endtime);
                if (stats != null) {
                    for (UsageStats stat : stats) {
                        mUsageStats.put(stat.getPackageName(), stat.getTotalTimeInForeground());
                    }
                }
            }
            mLastUsageUpdateTime = now;
        }
        return mUsageStats;
    }

    @Override
    public int compare(AppInfo a, AppInfo b) {
        String sortMode = LauncherPrefs.get(mContext).get(LauncherPrefs.APP_DRAWER_SORT_MODE);

        if ("install_date".equals(sortMode)) {
            int result = Long.compare(b.firstInstallTime, a.firstInstallTime);
            if (result != 0) return result;
        } else if ("usage".equals(sortMode)) {
            Map<String, Long> stats = getUsageStats();
            long usageA = stats.containsKey(a.componentName.getPackageName()) ? stats.get(a.componentName.getPackageName()) : 0L;
            long usageB = stats.containsKey(b.componentName.getPackageName()) ? stats.get(b.componentName.getPackageName()) : 0L;
            int result = Long.compare(usageB, usageA);
            if (result != 0) return result;
        }

        int result = mLabelComparator.compare(getSortingTitle(a), getSortingTitle(b));
        if (result != 0) {
            return result;
        }

        // If labels are same, compare component names
        result = a.componentName.compareTo(b.componentName);
        if (result != 0) {
            return result;
        }

        if (mMyUser.equals(a.user)) {
            return -1;
        } else {
            Long aUserSerial = mUserManager.getSerialNumberForUser(a.user);
            Long bUserSerial = mUserManager.getSerialNumberForUser(b.user);
            return aUserSerial.compareTo(bUserSerial);
        }
    }

    private String getSortingTitle(AppInfo info) {
        if (!TextUtils.isEmpty(info.appTitle)) {
            return info.appTitle.toString();
        }
        if (info.title != null) {
            return info.title.toString();
        }
        return "";
    }
}
