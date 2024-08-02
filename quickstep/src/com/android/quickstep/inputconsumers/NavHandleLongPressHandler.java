/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) 2024 Neoteric OS
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

import android.app.contextualsearch.ContextualSearchManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.NavHandle;
import com.android.quickstep.util.AssistUtils;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler {

    private Context mContext;
    private AssistUtils mAssistUtils;

    private static final VibrationEffect EFFECT_HEAVY_CLICK =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
    private static final VibrationEffect EFFECT_TICK =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);

    private static final Handler mHandler = new Handler(Looper.getMainLooper());

    public NavHandleLongPressHandler(Context context) {
        mContext = context;
        mAssistUtils = new AssistUtils(mContext);
    }

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public static NavHandleLongPressHandler newInstance(Context context) {
        return new NavHandleLongPressHandler(context);
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
        if (mAssistUtils.canDoContextualSearch()) {
            VibratorWrapper.INSTANCE.get(mContext).vibrate(EFFECT_TICK);
            navHandle.animateNavBarLongPress(true, true, 200L);
            return new Runnable() {
                @Override
                public final void run() {
                    mHandler.postDelayed(() -> {
                        if (mAssistUtils.invokeContextualSearch(
                                ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
                            VibratorWrapper.INSTANCE.get(mContext).vibrate(EFFECT_HEAVY_CLICK);
                        }
                    }, ViewConfiguration.getLongPressTimeout());
                }
            };
        } else {
            return null;
        }
    }

    /**
     * Called when nav handle gesture starts.
     *
     * @param navHandle to handle the animation for this touch
     */
    public void onTouchStarted(NavHandle navHandle) {}

    /**
     * Called when nav handle gesture is finished by the user lifting their finger or the system
     * cancelling the touch for some other reason.
     *
     * @param navHandle to handle the animation for this touch
     * @param reason why the touch ended
     */
    public void onTouchFinished(NavHandle navHandle, String reason) {
        navHandle.animateNavBarLongPress(false, true, 200L);
    }
}
