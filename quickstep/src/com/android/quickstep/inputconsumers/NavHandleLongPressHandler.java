/*
 * Copyright (C) 2023 The Android Open Source Project
 *               2023-2024 The risingOS Android Project
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
package com.android.quickstep.inputconsumers;

import static android.os.VibrationEffect.createPredefined;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.quickstep.util.ImageActionUtils;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.Utilities;
import com.android.quickstep.NavHandle;
import com.android.quickstep.TopTaskTracker;

import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.List;

import com.android.internal.util.android.VibrationUtils;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler implements ResourceBasedOverride {

    private final String TAG = "NavHandleLongPressHandler";
    private boolean DEBUG = false;

    private ThumbnailData mThumbnailData;
    private TopTaskTracker mTopTaskTracker;
    private Context mContext;

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public NavHandleLongPressHandler(Context context, TopTaskTracker topTaskTracker) {
        mContext = context;
        mTopTaskTracker = topTaskTracker;
    }

    /**
     * Called when nav handle is long pressed to get the Runnable that should be executed by the
     * caller to invoke long press behavior. If null is returned that means long press couldn't be
     * handled.
     * <p>
     * A Runnable is returned here to ensure the InputConsumer can call
     * {@link android.view.InputMonitor#pilferPointers()} before invoking the long press behavior
     * since pilfering can break the long press behavior.
     *
     * @param navHandle to handle this long press
     */
    public @Nullable Runnable getLongPressRunnable(NavHandle navHandle) {
	    if (!Utilities.isGSAEnabled(mContext) ||
            !Utilities.isLongPressToSearchEnabled(mContext)) {
            return null;
        }
        updateThumbnail();
        VibrationUtils.triggerVibration(mContext, 2);
        if (mThumbnailData != null && mThumbnailData.getThumbnail() != null) {
            if (DEBUG) Log.d(TAG, "getLongPressRunnable: Google lens should start now");
            ImageActionUtils.startLensActivity(mContext, mThumbnailData.getThumbnail(), null, TAG);
        } else {
            if (DEBUG) Log.d(TAG, "getLongPressRunnable: thumbnail is null");
        }
        return null;
    }

    /**
     * Called when nav handle gesture starts.
     *
     * @param navHandle to handle the animation for this touch
     */
    public void onTouchStarted(NavHandle navHandle) {
        updateThumbnail();
    }

    private void updateThumbnail() {
	if (!Utilities.isGSAEnabled(mContext)) {
            return;
        }
        String runningPackage = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getPackageName();
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            List<RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);
            for (RunningTaskInfo task : tasks) {
                if (task.topActivity.getPackageName().equals(runningPackage)) {
                    int taskId = task.id;
                    mThumbnailData = ActivityManagerWrapper.getInstance().takeTaskThumbnail(taskId);
                    break;
                }
            }
        }
        if (DEBUG) Log.d(TAG, "updateThumbnail running, runningPackage: " + runningPackage);
    }

    /**
     * Called when nav handle gesture is finished by the user lifting their finger or the system
     * cancelling the touch for some other reason.
     *
     * @param navHandle to handle the animation for this touch
     * @param reason why the touch ended
     */
    public void onTouchFinished(NavHandle navHandle, String reason) {}
}
