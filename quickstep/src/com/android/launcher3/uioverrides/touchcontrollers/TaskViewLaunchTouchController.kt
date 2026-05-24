/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.uioverrides.touchcontrollers

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Utilities.EDGE_NAV_BAR
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.display.DisplayController
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.TouchController
import com.android.quickstep.views.RecentsDismissUtils
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.google.android.msdl.data.model.MSDLToken
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Touch controller which handles dragging task view cards for launch. */
class TaskViewLaunchTouchController<CONTAINER>
@JvmOverloads
constructor(
    private val container: CONTAINER,
    @Suppress("UNUSED_PARAMETER")
    onAnimationCreatedCallback: Consumer<AnimatorPlaybackController>? = null,
) : TouchController, SingleAxisSwipeDetector.Listener
    where CONTAINER : Context, CONTAINER : RecentsViewContainer {
    private val tempRect = Rect()
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val downDirection = recentsView.pagedOrientationHandler.getDownDirection(isRtl)

    private var taskBeingDragged: TaskView? = null
    private var settleAnimation: RecentsDismissUtils.SpringSet? = null
    private var maxLockDisplacement: Float = 0f
    private var verticalFactor: Int = 0
    private var canInterceptTouch = false
    private var isDragging = false
    private var wasLockedBeforeDrag = false
    private var isBeyondLockThreshold = false

    private fun canInterceptTouch(ev: MotionEvent): Boolean =
        when {
            // Don't intercept swipes on the nav bar, as user might be trying to go home during a
            // task dismiss animation.
            (ev.edgeFlags and EDGE_NAV_BAR) != 0 -> {
                debugLog(TAG, "Not intercepting edge swipe on nav bar.")
                false
            }

            AbstractFloatingView.getTopOpenViewWithType(
                container,
                AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT,
            ) != null -> {
                debugLog(TAG, "Not intercepting, open floating view blocking touch.")
                false
            }

            !recentsView.scroller.isFinished -> {
                debugLog(TAG, "Not intercepting touch, recents scrolling.")
                false
            }

            !recentsView.stateManager.state.isTaskViewInteractive -> {
                debugLog(TAG, "Not intercepting touch, recents not interactive.")
                false
            }

            !recentsView.shouldSwipeDownLaunchTaskView(taskBeingDragged) -> {
                // Already logged in RecentsViewUtils.
                false
            }

            taskBeingDragged?.getLockablePackages().isNullOrEmpty() -> {
                debugLog(TAG, "Not intercepting touch, task cannot be locked.")
                false
            }

            !DisplayController.getNavigationMode(container).hasGestures -> {
                debugLog(TAG, "Not intercepting touch, not gesture mode.")
                false
            }

            else -> true
        }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            settleAnimation?.speedUpSpringsToEnd()
            canInterceptTouch = onActionDown(ev)
            if (!canInterceptTouch) {
                clearState()
                return false
            }
        }
        // Ignore other actions if touch intercepting has not been enabled in an ACTION_DOWN event.
        if (!canInterceptTouch) {
            return false
        }
        onControllerTouchEvent(ev)
        val downDirectionIsNegative = downDirection == SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        val wasInitialTouchDown =
            (downDirectionIsNegative && !detector.wasInitialTouchPositive()) ||
                (!downDirectionIsNegative && detector.wasInitialTouchPositive())
        return detector.isDraggingState && wasInitialTouchDown
    }

    override fun onControllerTouchEvent(ev: MotionEvent) = detector.onTouchEvent(ev)

    private fun onActionDown(ev: MotionEvent): Boolean {
        taskBeingDragged =
            recentsView.taskViews
                .firstOrNull {
                    recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
                }
                ?.also {
                    verticalFactor =
                        recentsView.pagedOrientationHandler.getTaskDragDisplacementFactor(isRtl)
                }
        if (!canInterceptTouch(ev)) {
            return false
        }
        detector.setDetectableScrollConditions(downDirection, /* ignoreSlop= */ false)
        return true
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling lock touch event.")

        val secondaryLayerDimension: Int =
            recentsView.pagedOrientationHandler.getSecondaryDimension(container.dragLayer)
        taskBeingDragged.getThumbnailBounds(tempRect, /* relativeToDragLayer= */ true)
        maxLockDisplacement =
            ceil(
                recentsView.pagedOrientationHandler.getTaskDismissLength(
                    secondaryLayerDimension,
                    tempRect,
                ) * LOCK_DISPLACEMENT_FRACTION
            ) * verticalFactor

        isDragging = true
        isBeyondLockThreshold = false
        wasLockedBeforeDrag = taskBeingDragged.isLocked
        taskBeingDragged.isBeingDraggedForDismissal = true
        taskBeingDragged.translationZ = 0.1f

        container.actionsView?.showLockPill(wasLockedBeforeDrag)
    }

    override fun onDrag(displacement: Float): Boolean {
        val taskBeingDragged = taskBeingDragged ?: return false
        if (maxLockDisplacement == 0f) return true
        val progress = boundToRange(displacement / maxLockDisplacement, 0f, 1f)
        val translation = progress * maxLockDisplacement
        taskBeingDragged.secondaryDismissTranslationProperty.setValue(taskBeingDragged, translation)
        setLiveTileTranslation(taskBeingDragged, translation, onlyIfDrawingLiveTile = true)
        updateLockThreshold(progress)
        return true
    }

    private fun updateLockThreshold(progress: Float) {
        val isBeyond = progress >= LOCK_THRESHOLD_FRACTION
        if (isBeyond == isBeyondLockThreshold) return
        isBeyondLockThreshold = isBeyond
        playThresholdHaptic()
    }

    private fun playThresholdHaptic() {
        MSDLPlayerWrapper.INSTANCE.get(recentsView.context)
            .playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
    }

    override fun onDragEnd(velocity: Float) {
        val taskBeingDragged = taskBeingDragged ?: return

        val currentDisplacement =
            taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
        val progress =
            if (maxLockDisplacement == 0f) 0f
            else boundToRange(currentDisplacement / maxLockDisplacement, 0f, 1f)
        val isFling = detector.isFling(velocity)
        val isFlingingTowardsLock =
            isFling && !recentsView.pagedOrientationHandler.isGoingUp(velocity, isRtl)
        val isFlingingTowardsRestState = isFling && !isFlingingTowardsLock
        val shouldToggleLock =
            isFlingingTowardsLock ||
                (progress >= LOCK_THRESHOLD_FRACTION && !isFlingingTowardsRestState)

        if (shouldToggleLock) {
            // Confirm a fling-triggered toggle that never crossed the threshold.
            if (!isBeyondLockThreshold) playThresholdHaptic()
            taskBeingDragged.toggleLockState()
        }

        isDragging = false
        taskBeingDragged.isBeingDraggedForDismissal = false
        container.actionsView?.hideLockPill()

        val dismissLength = abs(maxLockDisplacement).roundToInt()
        settleAnimation =
            recentsView.runTaskDismissSettlingSpringAnimation(
                taskBeingDragged,
                /* isDismissing= */ false,
                RecentsDismissUtils.DismissedTaskData(
                    startVelocity = velocity,
                    dismissLength = dismissLength,
                    finalPosition = 0f,
                    dismissThreshold = (LOCK_THRESHOLD_FRACTION * maxLockDisplacement).roundToInt(),
                ),
                /* shouldRemoveTaskView= */ false,
                /* isSplitSelection= */ false,
            )
        val settle = settleAnimation
        if (settle == null) {
            resetDraggedTask(taskBeingDragged)
        } else {
            settle.addEndListener {
                resetDraggedTask(taskBeingDragged)
                taskBeingDragged.isBeingDismissed = false
                if (settleAnimation === settle) settleAnimation = null
            }
        }

        this.taskBeingDragged = null
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
    }

    private fun resetDraggedTask(taskView: TaskView) {
        taskView.secondaryDismissTranslationProperty.setValue(taskView, 0f)
        setLiveTileTranslation(taskView, 0f, onlyIfDrawingLiveTile = false)
        taskView.translationZ = 0f
        taskView.isBeingDraggedForDismissal = false
    }

    private fun setLiveTileTranslation(
        taskView: TaskView,
        translation: Float,
        onlyIfDrawingLiveTile: Boolean,
    ) {
        if (!taskView.isRunningTask) return
        if (onlyIfDrawingLiveTile && !recentsView.enableDrawingLiveTile) return
        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value = translation
        }
        recentsView.redrawLiveTile()
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        if (isDragging) {
            taskBeingDragged?.let { resetDraggedTask(it) }
            container.actionsView?.hideLockPill()
        }
        isDragging = false
        isBeyondLockThreshold = false
        taskBeingDragged = null
    }

    companion object {
        private const val TAG = "TaskViewLaunchTouchController"

        // How far the card travels, as a fraction of its dismiss length.
        private const val LOCK_DISPLACEMENT_FRACTION = 0.4f

        // Fraction of the travel past which releasing toggles the lock.
        private const val LOCK_THRESHOLD_FRACTION = 0.5f
    }
}
