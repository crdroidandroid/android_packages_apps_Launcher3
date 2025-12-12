/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.Utilities.debugLog;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_BOTH;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Rect;
import android.os.VibrationEffect;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.VibrationConstants;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;

/**
 * Touch controller for handling task view card swipes
 *
 * @deprecated This class will be replaced by the new {@link TaskViewTouchController}.
 */
@Deprecated
public class TaskViewTouchControllerDeprecated<
        CONTAINER extends Context & RecentsViewContainer> extends AnimatorListenerAdapter
        implements TouchController, SingleAxisSwipeDetector.Listener {
    private static final String TAG = "TaskViewTouchControllerDeprecated";

    private static final float ANIMATION_PROGRESS_FRACTION_MIDPOINT = 0.5f;
    private static final long MIN_TASK_DISMISS_ANIMATION_DURATION = 300;
    private static final long MAX_TASK_DISMISS_ANIMATION_DURATION = 600;

    public static final int TASK_DISMISS_VIBRATION_PRIMITIVE =
            VibrationEffect.Composition.PRIMITIVE_TICK;
    public static final float TASK_DISMISS_VIBRATION_PRIMITIVE_SCALE = 1f;
    public static final VibrationEffect TASK_DISMISS_VIBRATION_FALLBACK =
            VibrationConstants.EFFECT_TEXTURE_TICK;

    protected final CONTAINER mContainer;
    private final TaskViewRecentsTouchContext mTaskViewRecentsTouchContext;
    private final SingleAxisSwipeDetector mDetector;
    private final RecentsView<?, ?> mRecentsView;
    private final Rect mTempRect = new Rect();
    private final boolean mIsRtl;

    private AnimatorPlaybackController mCurrentAnimation;
    private boolean mCurrentAnimationIsGoingUp;
    private boolean mAllowGoingUp;
    private boolean mAllowGoingDown;

    private boolean mNoIntercept;

    private float mDisplacementShift;
    private float mProgressMultiplier;
    private float mEndDisplacement;
    private boolean mDraggingEnabled = true;
    private FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();
    private Float mOverrideVelocity = null;

    private TaskView mTaskBeingDragged;
    private float mTaskDragStartTranslationZ = 0f;

    private boolean mIsDismissHapticRunning = false;

    public TaskViewTouchControllerDeprecated(CONTAINER container,
            TaskViewRecentsTouchContext taskViewRecentsTouchContext) {
        mContainer = container;
        mTaskViewRecentsTouchContext = taskViewRecentsTouchContext;
        mRecentsView = container.getOverviewPanel();
        mIsRtl = Utilities.isRtl(container.getResources());
        SingleAxisSwipeDetector.Direction dir =
                mRecentsView.getPagedOrientationHandler().getUpDownSwipeDirection();
        mDetector = new SingleAxisSwipeDetector(container, this, dir);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if ((ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) != 0) {
            // Don't intercept swipes on the nav bar, as user might be trying to go home
            // during a task dismiss animation.
            if (mCurrentAnimation != null) {
                mCurrentAnimation.getAnimationPlayer().end();
            }
            debugLog(TAG, "Not intercepting edge swipe on nav bar.");
            return false;
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.forceFinishIfCloseToEnd();
        }
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenViewWithType(
                mContainer, TYPE_TOUCH_CONTROLLER_NO_INTERCEPT) != null) {
            debugLog(TAG, "Not intercepting, open floating view blocking touch.");
            return false;
        }
        return mTaskViewRecentsTouchContext.isRecentsInteractive();
    }

    /**
     * Returns the topmost TaskView under the event (prefers larger Z, then later draw order).
     * This prevents dragging the wrong card when task views overlap.
     */
    private TaskView findTopMostTaskUnderEvent(MotionEvent ev) {
        if (mTaskViewRecentsTouchContext.isRecentsModal()) {
            return null;
        }

        final BaseDragLayer dragLayer = mContainer.getDragLayer();
        TaskView best = null;
        float bestZ = Float.NEGATIVE_INFINITY;
        int bestIndex = Integer.MIN_VALUE;
        final float eps = 1e-4f;

        for (TaskView taskView : mRecentsView.getTaskViews()) {
            if (!mRecentsView.isTaskViewVisible(taskView)) continue;
            if (!dragLayer.isEventOverView(taskView, ev)) continue;

            final float z = taskView.getZ();
            final int index = mRecentsView.indexOfChild(taskView);

            if (best == null
                    || z > bestZ + eps
                    || (Math.abs(z - bestZ) <= eps && index > bestIndex)) {
                best = taskView;
                bestZ = z;
                bestIndex = index;
            }
        }
        return best;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getTarget()) {
            clearState();
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if ((ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)
                && mCurrentAnimation == null) {
            clearState();
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                debugLog(TAG, "Not intercepting touch.");
                return false;
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            int directionsToDetectScroll = 0;
            boolean ignoreSlopWhenSettling = false;
            if (mCurrentAnimation != null) {
                directionsToDetectScroll = DIRECTION_BOTH;
                ignoreSlopWhenSettling = true;
            } else {
                mTaskBeingDragged = findTopMostTaskUnderEvent(ev);

                if (mTaskBeingDragged != null) {
                    int upDirection = mRecentsView.getPagedOrientationHandler()
                            .getUpDirection(mIsRtl);

                    // The task can be dragged up to dismiss it.
                    mAllowGoingUp = true;

                    // The task can be dragged down to open it if:
                    // - It's the current page
                    // - We support gestures to enter overview
                    // - It's the focused task if in grid view
                    // - The task is snapped
                    mAllowGoingDown = mTaskBeingDragged == mRecentsView.getCurrentPageTaskView()
                            && DisplayController.getNavigationMode(mContainer).hasGestures
                            && (!mRecentsView.showAsGrid() || mTaskBeingDragged.isLargeTile())
                            && mRecentsView.isTaskInExpectedScrollPosition(mTaskBeingDragged);

                    directionsToDetectScroll = mAllowGoingDown ? DIRECTION_BOTH : upDirection;
                }

                if (mTaskBeingDragged == null) {
                    mNoIntercept = true;
                    debugLog(TAG, "Not intercepting touch, no task to drag.");
                    return false;
                }
            }

            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);
        }

        if (mNoIntercept) {
            debugLog(TAG, "Not intercepting touch.");
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    private void reInitAnimationController(boolean goingUp) {
        if (mCurrentAnimation != null && mCurrentAnimationIsGoingUp == goingUp) {
            // No need to init
            return;
        }
        if ((goingUp && !mAllowGoingUp) || (!goingUp && !mAllowGoingDown)) {
            // Trying to re-init in an unsupported direction.
            return;
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setPlayFraction(0);
            mCurrentAnimation.getTarget().removeListener(this);
            mCurrentAnimation.dispatchOnCancel();
        }

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        mCurrentAnimationIsGoingUp = goingUp;
        BaseDragLayer dl = mContainer.getDragLayer();
        final int secondaryLayerDimension = orientationHandler.getSecondaryDimension(dl);
        long maxDuration = 2 * secondaryLayerDimension;
        int verticalFactor = orientationHandler.getTaskDragDisplacementFactor(mIsRtl);
        int secondaryTaskDimension = orientationHandler.getSecondaryDimension(mTaskBeingDragged);
        // The interpolator controlling the most prominent visual movement. We use this to determine
        // whether we passed SUCCESS_TRANSITION_PROGRESS.
        final Interpolator currentInterpolator;
        PendingAnimation pa;
        if (goingUp) {
            currentInterpolator = Interpolators.LINEAR;
            pa = new PendingAnimation(maxDuration);
            mRecentsView.createTaskDismissAnimation(pa, mTaskBeingDragged,
                    true /* animateTaskView */, true /* removeTask */, maxDuration,
                    false /* dismissingForSplitSelection*/, null /* gridEndData */);

            mEndDisplacement = -secondaryTaskDimension;
        } else {
            currentInterpolator = Interpolators.ZOOM_IN;
            pa = mRecentsView.createTaskLaunchAnimation(
                    mTaskBeingDragged, maxDuration, currentInterpolator);

            // Since the thumbnail is what is filling the screen, based the end displacement on it.
            mTaskBeingDragged.getThumbnailBounds(mTempRect, /*relativeToDragLayer=*/true);
            mEndDisplacement = secondaryLayerDimension - mTempRect.bottom;
        }
        mEndDisplacement *= verticalFactor;
        mCurrentAnimation = pa.createPlaybackController();

        // Setting this interpolator doesn't affect the visual motion, but is used to determine
        // whether we successfully reached the target state in onDragEnd().
        mCurrentAnimation.getTarget().setInterpolator(currentInterpolator);
        mTaskViewRecentsTouchContext.onUserControlledAnimationCreated(mCurrentAnimation);
        mCurrentAnimation.getTarget().addListener(this);
        mCurrentAnimation.dispatchOnStart();
        mProgressMultiplier = 1 / mEndDisplacement;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        if (!mDraggingEnabled) return;
        debugLog(TAG, "Handling touch.");

        // Bring the dragged task above neighbors during the gesture; restore in clearState().
        if (mTaskBeingDragged != null) {
            mTaskDragStartTranslationZ = mTaskBeingDragged.getTranslationZ();
            mTaskBeingDragged.setTranslationZ(Math.max(mTaskDragStartTranslationZ, 0.1f));
        }

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        if (mCurrentAnimation == null) {
            reInitAnimationController(orientationHandler.isGoingUp(startDisplacement, mIsRtl));
            mDisplacementShift = 0;
        } else {
            mDisplacementShift = mCurrentAnimation.getProgressFraction() / mProgressMultiplier;
            mCurrentAnimation.pause();
        }
        mFlingBlockCheck.unblockFling();
        mOverrideVelocity = null;
    }

    @Override
    public boolean onDrag(float displacement) {
        if (!mDraggingEnabled) return true;

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        float totalDisplacement = displacement + mDisplacementShift;
        boolean isGoingUp = totalDisplacement == 0 ? mCurrentAnimationIsGoingUp :
                orientationHandler.isGoingUp(totalDisplacement, mIsRtl);

        if (isGoingUp != mCurrentAnimationIsGoingUp) {
            reInitAnimationController(isGoingUp);
            mFlingBlockCheck.blockFling();
        } else {
            mFlingBlockCheck.onEvent();
        }

        // Drag-up matches drag-down: always track finger across the full range.
        mCurrentAnimation.setPlayFraction(
                Utilities.boundToRange(totalDisplacement * mProgressMultiplier, 0, 1));

        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mOverrideVelocity != null) {
            velocity = mOverrideVelocity;
            mOverrideVelocity = null;
        }
        // Limit velocity, as very large scalar values make animations play too quickly
        float maxTaskDismissDragVelocity = mTaskBeingDragged.getResources().getDimension(
                R.dimen.max_task_dismiss_drag_velocity);
        velocity = Utilities.boundToRange(velocity, -maxTaskDismissDragVelocity,
                maxTaskDismissDragVelocity);
        boolean fling = mDraggingEnabled && mDetector.isFling(velocity);
        final boolean goingToEnd;
        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }
        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        boolean goingUp = orientationHandler.isGoingUp(velocity, mIsRtl);
        float progress = mCurrentAnimation.getProgressFraction();
        float interpolatedProgress = mCurrentAnimation.getInterpolatedProgress();
        if (fling) {
            goingToEnd = goingUp == mCurrentAnimationIsGoingUp;
        } else {
            goingToEnd = interpolatedProgress > SUCCESS_TRANSITION_PROGRESS;
        }
        long animationDuration = BaseSwipeDetector.calculateDuration(
                velocity, goingToEnd ? (1 - progress) : progress);
        if (blockedFling && !goingToEnd) {
            animationDuration *= LauncherAnimUtils.blockedFlingDurationFactor(velocity);
        }
        // Due to very high or low velocity dismissals, animation durations can be inconsistently
        // long or short. Bound the duration for animation of task translations for a more
        // standardized feel.
        animationDuration = Utilities.boundToRange(animationDuration,
                MIN_TASK_DISMISS_ANIMATION_DURATION, MAX_TASK_DISMISS_ANIMATION_DURATION);

        mCurrentAnimation.setEndAction(this::clearState);
        mCurrentAnimation.startWithVelocity(mContainer, goingToEnd, Math.abs(velocity),
                mEndDisplacement, animationDuration);
        if (goingUp && goingToEnd && !mIsDismissHapticRunning) {
            VibratorWrapper.INSTANCE.get(mContainer).vibrate(TASK_DISMISS_VIBRATION_PRIMITIVE,
                    TASK_DISMISS_VIBRATION_PRIMITIVE_SCALE, TASK_DISMISS_VIBRATION_FALLBACK);
            mIsDismissHapticRunning = true;
        }

        mDraggingEnabled = true;
    }

    private void clearState() {
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
        mDraggingEnabled = true;

        // Restore Z if we modified it during drag.
        if (mTaskBeingDragged != null) {
            mTaskBeingDragged.setTranslationZ(mTaskDragStartTranslationZ);
        }
        mTaskDragStartTranslationZ = 0f;

        mTaskBeingDragged = null;
        mCurrentAnimation = null;
        mIsDismissHapticRunning = false;
    }
}
