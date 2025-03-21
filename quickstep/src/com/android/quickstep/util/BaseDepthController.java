/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;

import android.app.WallpaperManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.IBinder;
import android.util.FloatProperty;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;

import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.systemui.shared.system.BlurUtils;

/**
 * Utility class for applying depth effect
 */
public class BaseDepthController {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_60_PERCENT = 0.6f;
    public static final float DEPTH_70_PERCENT = 0.7f;

    private static final FloatProperty<BaseDepthController> DEPTH =
            new FloatProperty<BaseDepthController>("depth") {
                @Override
                public void setValue(BaseDepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(BaseDepthController depthController) {
                    return depthController.mDepth;
                }
            };

    private static final int DEPTH_INDEX_STATE_TRANSITION = 0;
    private static final int DEPTH_INDEX_WIDGET = 1;
    private static final int DEPTH_INDEX_COUNT = 2;

    // b/291401432
    private static final String TAG = "BaseDepthController";

    protected final Launcher mLauncher;
    /** Property to set the depth for state transition. */
    public final MultiProperty stateDepth;
    /** Property to set the depth for widget picker. */
    public final MultiProperty widgetDepth;

    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    protected final int mMaxBlurRadius;
    protected final WallpaperManager mWallpaperManager;
    protected boolean mCrossWindowBlursEnabled;

    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     *
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    protected SurfaceControl mBaseSurface;

    protected SurfaceControl mBaseSurfaceOverride;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    /** Pause blur but allow transparent, can be used when launch something behind the Launcher. */
    protected boolean mPauseBlurs;

    /**
     * Last blur value, in pixels, that was applied.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    protected boolean mWaitingOnSurfaceValidity;

    private SurfaceControl mBlurSurface = null;

    public BaseDepthController(Launcher activity) {
        mLauncher = activity;
        if (Flags.allAppsBlur()) {
            mMaxBlurRadius = activity.getResources().getDimensionPixelSize(
                    R.dimen.max_depth_blur_radius_enhanced);
        } else {
            mMaxBlurRadius = activity.getResources().getInteger(R.integer.max_depth_blur_radius);
        }
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);

        MultiPropertyFactory<BaseDepthController> depthProperty =
                new MultiPropertyFactory<>(this, DEPTH, DEPTH_INDEX_COUNT, Float::max);
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION);
        widgetDepth = depthProperty.get(DEPTH_INDEX_WIDGET);
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        mCrossWindowBlursEnabled = isEnabled;
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    public void pauseBlursOnWindows(boolean pause) {
        if (pause != mPauseBlurs) {
            mPauseBlurs = pause;
            applyDepthAndBlur();
        }
    }

    protected void onInvalidSurface() { }

    protected void applyDepthAndBlur() {
        applyDepthAndBlur(createTransaction());
    }

    protected void applyDepthAndBlur(SurfaceControl.Transaction transaction) {
        try (transaction) {
            float depth = mDepth;
            IBinder windowToken = mLauncher.getRootView().getWindowToken();
            if (windowToken != null) {
                if (enableScalingRevealHomeAnimation()) {
                    mWallpaperManager.setWallpaperZoomOut(windowToken, depth);
                } else {
                    // The API's full zoom-out is three times larger than the zoom-out we apply
                    // to the
                    // icons. To keep the two consistent throughout the animation while keeping
                    // Launcher's concept of full depth unchanged, we divide the depth by 3 here.
                    mWallpaperManager.setWallpaperZoomOut(windowToken, depth / 3);
                }
            }

            if (!BlurUtils.supportsBlursOnWindows()) {
                return;
            }
            if (mBaseSurface == null) {
                Log.d(TAG, "mSurface is null and mCurrentBlur is: " + mCurrentBlur);
                return;
            }
            if (!mBaseSurface.isValid()) {
                Log.d(TAG, "mSurface is not valid");
                mWaitingOnSurfaceValidity = true;
                onInvalidSurface();
                return;
            }
            mWaitingOnSurfaceValidity = false;
            boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
            boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg && !mPauseBlurs;

            float blurAmount;
            if (enableScalingRevealHomeAnimation()) {
                blurAmount = mapDepthToBlur(depth);
            } else {
                blurAmount = depth;
            }
            mCurrentBlur = !mCrossWindowBlursEnabled || hasOpaqueBg || mPauseBlurs
                    ? 0 : (int) (blurAmount * mMaxBlurRadius);

            SurfaceControl blurSurface =
                    enableOverviewBackgroundWallpaperBlur() && mBlurSurface != null ? mBlurSurface
                            : mBaseSurface;

            transaction.setBackgroundBlurRadius(blurSurface, mCurrentBlur)
                    .setOpaque(blurSurface, isSurfaceOpaque);

            // Set early wake-up flags when we know we're executing an expensive operation, this way
            // SurfaceFlinger will adjust its internal offsets to avoid jank.
            boolean wantsEarlyWakeUp = depth > 0 && depth < 1;
            if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
                transaction.setEarlyWakeupStart();
                mInEarlyWakeUp = true;
            } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
                transaction.setEarlyWakeupEnd();
                mInEarlyWakeUp = false;
            }

            AttachedSurfaceControl rootSurfaceControl =
                    mLauncher.getRootView().getRootSurfaceControl();
            if (rootSurfaceControl != null) {
                rootSurfaceControl.applyTransactionOnDraw(transaction);
            }
        }

        blurWorkspaceDepthTargets();
    }

    private void blurWorkspaceDepthTargets() {
        if (!Flags.allAppsBlur()) {
            return;
        }
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        // Only blur workspace if the current and target state want it blurred.
        boolean shouldBlurWorkspace = stateManager.getCurrentStableState().shouldBlurWorkspace()
                && stateManager.getState().shouldBlurWorkspace();
        // If blur is not desired, apply 0 blur to force reset.
        int blurRadius = shouldBlurWorkspace ? mCurrentBlur : 0;
        RenderEffect blurEffect =
                RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.DECAL);
        mLauncher.getDepthBlurTargets().forEach(target -> target.setRenderEffect(blurEffect));
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Round out the depth to dedupe frequent, non-perceptable updates
        int depthI = (int) (depth * 256);
        float depthF = depthI / 256f;
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the lowest surface that should not be blurred.
     * <p>
     * Blur is applied to below {@link #mBaseSurfaceOverride}. When set to {@code null}, blur is
     * applied
     * to below {@link #mBaseSurface}.
     * </p>
     */
    public void setBaseSurfaceOverride(@Nullable SurfaceControl baseSurfaceOverride) {
        this.mBaseSurfaceOverride = baseSurfaceOverride;
        SurfaceControl.Transaction transaction = createTransaction();
        Log.d(TAG, "setBaseSurfaceOverride: applying blur behind leash " + baseSurfaceOverride);
        updateBlurSurfaceOrder(transaction);
        applyDepthAndBlur(transaction);
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setBaseSurface(SurfaceControl baseSurface) {
        if (mBaseSurface != baseSurface || mWaitingOnSurfaceValidity) {
            mBaseSurface = baseSurface;
            Log.d(TAG, "setSurface:\n\tmWaitingOnSurfaceValidity: " + mWaitingOnSurfaceValidity
                    + "\n\tmBaseSurface: " + mBaseSurface);
            if (enableOverviewBackgroundWallpaperBlur()) {
                SurfaceControl.Transaction transaction = createTransaction();
                if (mBaseSurface != null) {
                    if (mBlurSurface == null) {
                        mBlurSurface = new SurfaceControl.Builder()
                                .setName("Overview Blur")
                                .setHidden(false)
                                .build();
                        Log.d(TAG,
                                "setBaseSurface: creating Overview Blur surface " + mBlurSurface);
                    }
                    updateBlurSurfaceParent(transaction);
                    updateBlurSurfaceOrder(transaction);
                    applyDepthAndBlur(transaction);
                } else {
                    if (mBlurSurface != null) {
                        Log.d(TAG, "setBaseSurface: removing base surface " + mBlurSurface);
                        transaction.remove(mBlurSurface);
                        mBlurSurface = null;
                    }
                }
            } else {
                applyDepthAndBlur();
            }
        }
    }

    private void updateBlurSurfaceOrder(SurfaceControl.Transaction transaction) {
        SurfaceControl baseSurface = null;
        if (mBaseSurfaceOverride != null && mBaseSurfaceOverride.isValid()) {
            Log.d(TAG, "updateBlurSurfaceOrder: relayering to leash " + mBaseSurfaceOverride);
            baseSurface = mBaseSurfaceOverride;
        } else if (mBaseSurface != null && mBaseSurface.isValid()) {
            Log.d(TAG, "updateBlurSurfaceOrder: relayering to base " + mBaseSurface);
            baseSurface = mBaseSurface;
        }
        if (baseSurface != null && mBlurSurface != null) {
            // Set mBlurSurface to be 1 layer behind mBaseSurface or mBaseSurfaceOverride.
            transaction.setRelativeLayer(mBlurSurface, baseSurface, -1);
        }
    }

    private void updateBlurSurfaceParent(SurfaceControl.Transaction transaction) {
        if (mBlurSurface != null) {
            // Reparent to launcher for full screen blur.
            Log.d(TAG,
                    "updateBlurSurfaceParent: reparenting " + mBlurSurface + " to " + mBaseSurface);
            transaction.reparent(mBlurSurface, mBaseSurface);
        }
    }

    /**
     * Maps depth values to blur amounts as a percentage of the max blur.
     * The blur percentage grows linearly with depth, and maxes out at 30% depth.
     */
    private static float mapDepthToBlur(float depth) {
        return Math.min(3 * depth, 1f);
    }

    private SurfaceControl.Transaction createTransaction() {
        return new SurfaceControl.Transaction();
    }
}
