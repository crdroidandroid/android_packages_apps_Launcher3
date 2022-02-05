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
package com.android.quickstep.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.IdRes
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.core.view.updateLayoutParams
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags.enableCursorHoverStates
import com.android.launcher3.Flags.enableGridOnlyOverview
import com.android.launcher3.Flags.enableHoverOfChildElementsInTaskview
import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.testing.TestLogging
import com.android.launcher3.testing.shared.TestProtocol
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE
import com.android.launcher3.util.MultiValueAlpha
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.TraceHelper
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.rects.set
import com.android.quickstep.FullscreenDrawParams
import com.android.quickstep.RecentsModel
import com.android.quickstep.RemoteAnimationTargets
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskViewUtils
import com.android.quickstep.orientation.RecentsPagedOrientationHandler
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.util.ActiveGestureErrorDetector
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.RecentsOrientedState
import com.android.quickstep.util.TaskCornerRadius
import com.android.quickstep.util.TaskRemovedDuringLaunchListener
import com.android.quickstep.views.RecentsView.UNBOUND_TASK_VIEW_ID
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.ActivityManagerWrapper

/** A task in the Recents view. */
open class TaskView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    focusBorderAnimator: BorderAnimator? = null,
    hoverBorderAnimator: BorderAnimator? = null,
    val type: TaskViewType = TaskViewType.SINGLE,
    protected val thumbnailFullscreenParams: FullscreenDrawParams = FullscreenDrawParams(context),
) : FrameLayout(context, attrs), ViewPool.Reusable {
    /**
     * Used in conjunction with [onTaskListVisibilityChanged], providing more granularity on which
     * components of this task require an update
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(FLAG_UPDATE_ALL, FLAG_UPDATE_ICON, FLAG_UPDATE_THUMBNAIL, FLAG_UPDATE_CORNER_RADIUS)
    annotation class TaskDataChanges

    val taskIds: IntArray
        /** Returns a copy of integer array containing taskIds of all tasks in the TaskView. */
        get() = taskContainers.map { it.task.key.id }.toIntArray()

    val taskIdSet: Set<Int>
        /** Returns a copy of integer array containing taskIds of all tasks in the TaskView. */
        get() = taskContainers.map { it.task.key.id }.toSet()

    val snapshotViews: Array<View>
        get() = taskContainers.map { it.snapshotView }.toTypedArray()

    val isGridTask: Boolean
        /** Returns whether the task is part of overview grid and not being focused. */
        get() = container.deviceProfile.isTablet && !isLargeTile

    val isRunningTask: Boolean
        get() = this === recentsView?.runningTaskView

    val isLargeTile: Boolean
        get() =
            this == recentsView?.focusedTaskView ||
                (enableLargeDesktopWindowingTile() && type == TaskViewType.DESKTOP)

    val recentsView: RecentsView<*, *>?
        get() = parent as? RecentsView<*, *>

    val pagedOrientationHandler: RecentsPagedOrientationHandler
        get() = orientedState.orientationHandler

    @get:Deprecated("Use [taskContainers] instead.")
    val firstTask: Task
        /** Returns the first task bound to this TaskView. */
        get() = taskContainers[0].task

    @get:Deprecated("Use [taskContainers] instead.")
    val firstItemInfo: ItemInfo
        get() = taskContainers[0].itemInfo

    protected val container: RecentsViewContainer =
        RecentsViewContainer.containerFromContext(context)
    protected val lastTouchDownPosition = PointF()

    // Derived view properties
    protected val persistentScale: Float
        /**
         * Returns multiplication of scale that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state.
         */
        get() = Utilities.mapRange(gridProgress, nonGridScale, 1f)

    protected val persistentTranslationX: Float
        /**
         * Returns addition of translationX that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() =
            (getNonGridTrans(nonGridTranslationX) +
                getGridTrans(this.gridTranslationX) +
                getNonGridTrans(nonGridPivotTranslationX))

    protected val persistentTranslationY: Float
        /**
         * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does
         * not change according to a temporary state (e.g. task offset).
         */
        get() = boxTranslationY + getGridTrans(gridTranslationY)

    protected val primarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y,
            )

    protected val secondarySplitTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                SPLIT_SELECT_TRANSLATION_X,
                SPLIT_SELECT_TRANSLATION_Y,
            )

    protected val primaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    protected val secondaryDismissTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y)

    protected val primaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y,
            )

    protected val secondaryTaskOffsetTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_OFFSET_TRANSLATION_X,
                TASK_OFFSET_TRANSLATION_Y,
            )

    protected val taskResistanceTranslationProperty: FloatProperty<TaskView>
        get() =
            pagedOrientationHandler.getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X,
                TASK_RESISTANCE_TRANSLATION_Y,
            )

    private val tempCoordinates = FloatArray(2)
    private val focusBorderAnimator: BorderAnimator?
    private val hoverBorderAnimator: BorderAnimator?
    private val rootViewDisplayId: Int
        get() = rootView.display?.displayId ?: Display.DEFAULT_DISPLAY

    /** Returns a list of all TaskContainers in the TaskView. */
    lateinit var taskContainers: List<TaskContainer>
        protected set

    lateinit var orientedState: RecentsOrientedState

    var taskViewId = UNBOUND_TASK_VIEW_ID
    var isEndQuickSwitchCuj = false

    // Various animation progress variables.
    // progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
    protected var fullscreenProgress = 0f
        set(value) {
            field = Utilities.boundToRange(value, 0f, 1f)
            onFullscreenProgressChanged(field)
        }

    // gridProgress 0 = carousel; 1 = 2 row grid.
    protected var gridProgress = 0f
        set(value) {
            field = value
            onGridProgressChanged()
        }

    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview. 0 being in context with other tasks, 1 being shown on its own.
     */
    protected var modalness = 0f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            onModalnessUpdated(field)
        }

    protected var taskThumbnailSplashAlpha = 0f
        set(value) {
            field = value
            applyThumbnailSplashAlpha()
        }

    protected var nonGridScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissScale = 1f
        set(value) {
            field = value
            applyScale()
        }

    private var dismissTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var dismissTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskOffsetTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskOffsetTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var taskResistanceTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private var taskResistanceTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    // The following translation variables should only be used in the same orientation as Launcher.
    private var boxTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    // The following grid translations scales with mGridProgress.
    protected var gridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    var gridTranslationY = 0f
        protected set(value) {
            field = value
            applyTranslationY()
        }

    // The following grid translation is used to animate closing the gap between grid and clear all.
    private var gridEndTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    // Applied as a complement to gridTranslation, for adjusting the carousel overview and quick
    // switch.
    protected var nonGridTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    protected var nonGridPivotTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    // Used when in SplitScreenSelectState
    private var splitSelectTranslationY = 0f
        set(value) {
            field = value
            applyTranslationY()
        }

    private var splitSelectTranslationX = 0f
        set(value) {
            field = value
            applyTranslationX()
        }

    private val taskViewAlpha = MultiValueAlpha(this, NUM_ALPHA_CHANNELS)

    protected var stableAlpha
        set(value) {
            taskViewAlpha.get(ALPHA_INDEX_STABLE).value = value
        }
        get() = taskViewAlpha.get(ALPHA_INDEX_STABLE).value

    var attachAlpha
        set(value) {
            taskViewAlpha.get(ALPHA_INDEX_ATTACH).value = value
        }
        get() = taskViewAlpha.get(ALPHA_INDEX_ATTACH).value

    var splitAlpha
        set(value) {
            splitAlphaProperty.value = value
        }
        get() = splitAlphaProperty.value

    val splitAlphaProperty: MultiPropertyFactory<View>.MultiProperty
        get() = taskViewAlpha.get(ALPHA_INDEX_SPLIT)

    protected var shouldShowScreenshot = false
        get() = !isRunningTask || field
        private set

    /** Enable or disable showing border on hover and focus change */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    var borderEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            // Set the animation correctly in case it misses the hover/focus event during state
            // transition
            hoverBorderAnimator?.setBorderVisibility(visible = field && isHovered, animated = true)
            focusBorderAnimator?.setBorderVisibility(visible = field && isFocused, animated = true)
        }

    /**
     * Used to cache the hover border state so we don't repeatedly call the border animator with
     * every hover event when the user hasn't crossed the threshold of the [thumbnailBounds].
     */
    private var hoverBorderVisible = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            Log.d(
                TAG,
                "${taskIds.contentToString()} - setting border animator visibility to: $field",
            )
            hoverBorderAnimator?.setBorderVisibility(visible = field, animated = true)
        }

    // Used to cache thumbnail bounds to avoid recalculating on every hover move.
    private var thumbnailBounds = Rect()

    // Progress variable indicating if the TaskView is in a settled state:
    // 0 = The TaskView is in a transitioning state e.g. during gesture, in quickswitch carousel,
    // becoming focus task etc.
    // 1 = The TaskView is settled and no longer transitioning
    private var settledProgress = 1f
        set(value) {
            field = value
            onSettledProgressUpdated(field)
        }

    private val settledProgressPropertyFactory =
        MultiPropertyFactory(
            this,
            SETTLED_PROGRESS,
            SETTLED_PROGRESS_INDEX_COUNT,
            { x: Float, y: Float -> x * y },
            1f,
        )
    private val settledProgressFullscreen =
        settledProgressPropertyFactory.get(SETTLED_PROGRESS_INDEX_FULLSCREEN)
    private val settledProgressGesture =
        settledProgressPropertyFactory.get(SETTLED_PROGRESS_INDEX_GESTURE)
    private val settledProgressDismiss =
        settledProgressPropertyFactory.get(SETTLED_PROGRESS_INDEX_DISMISS)

    /**
     * Returns an animator of [settledProgressDismiss] that transition in with a built-in
     * interpolator.
     */
    fun getDismissIconFadeInAnimator(): ObjectAnimator =
        ObjectAnimator.ofFloat(settledProgressDismiss, MULTI_PROPERTY_VALUE, 1f).apply {
            duration = FADE_IN_ICON_DURATION
            interpolator = FADE_IN_ICON_INTERPOLATOR
        }

    /**
     * Returns an animator of [settledProgressDismiss] that transition out with a built-in
     * interpolator. [AnimatedFloat] is used to apply another level of interpolation, on top of
     * interpolator set to the [Animator] by the caller.
     */
    fun getDismissIconFadeOutAnimator(): ObjectAnimator =
        AnimatedFloat { v ->
                settledProgressDismiss.value =
                    SETTLED_PROGRESS_FAST_OUT_INTERPOLATOR.getInterpolation(v)
            }
            .animateToValue(1f, 0f)

    private var iconFadeInOnGestureCompleteAnimator: ObjectAnimator? = null
    // The current background requests to load the task thumbnail and icon
    private val pendingThumbnailLoadRequests = mutableListOf<CancellableTask<*>>()
    private val pendingIconLoadRequests = mutableListOf<CancellableTask<*>>()
    private var isClickableAsLiveTile = true

    init {
        setOnClickListener { _ -> onClick() }

        val cursorHoverStatesEnabled = enableCursorHoverStates()
        setWillNotDraw(!cursorHoverStatesEnabled)
        context.obtainStyledAttributes(attrs, R.styleable.TaskView, defStyleAttr, defStyleRes).use {
            this.focusBorderAnimator =
                focusBorderAnimator
                    ?: createSimpleBorderAnimator(
                        TaskCornerRadius.get(context).toInt(),
                        context.resources.getDimensionPixelSize(
                            R.dimen.keyboard_quick_switch_border_width
                        ),
                        { bounds: Rect -> getThumbnailBounds(bounds) },
                        this,
                        it.getColor(
                            R.styleable.TaskView_focusBorderColor,
                            BorderAnimator.DEFAULT_BORDER_COLOR,
                        ),
                    )
            this.hoverBorderAnimator =
                hoverBorderAnimator
                    ?: if (cursorHoverStatesEnabled)
                        createSimpleBorderAnimator(
                            TaskCornerRadius.get(context).toInt(),
                            context.resources.getDimensionPixelSize(
                                R.dimen.task_hover_border_width
                            ),
                            { bounds: Rect -> getThumbnailBounds(bounds) },
                            this,
                            it.getColor(
                                R.styleable.TaskView_hoverBorderColor,
                                BorderAnimator.DEFAULT_BORDER_COLOR,
                            ),
                        )
                    else null
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (borderEnabled) {
            focusBorderAnimator?.setBorderVisibility(gainFocus, /* animated= */ true)
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (borderEnabled) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    hoverBorderVisible =
                        if (enableHoverOfChildElementsInTaskview()) {
                            getThumbnailBounds(thumbnailBounds)
                            event.isWithinThumbnailBounds()
                        } else {
                            true
                        }
                }
                MotionEvent.ACTION_HOVER_MOVE ->
                    if (enableHoverOfChildElementsInTaskview())
                        hoverBorderVisible = event.isWithinThumbnailBounds()
                MotionEvent.ACTION_HOVER_EXIT -> hoverBorderVisible = false
                else -> {}
            }
        }
        return super.onHoverEvent(event)
    }

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean =
        if (enableHoverOfChildElementsInTaskview()) super.onInterceptHoverEvent(event)
        else if (enableCursorHoverStates()) true else super.onInterceptHoverEvent(event)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val recentsView = recentsView ?: return false
        val splitSelectStateController = recentsView.splitSelectController
        // Disable taps for split selection animation unless we have a task not being selected
        if (
            splitSelectStateController.isSplitSelectActive &&
                taskContainers.none { it.task.key.id != splitSelectStateController.initialTaskId }
        ) {
            return false
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            with(lastTouchDownPosition) {
                x = ev.x
                y = ev.y
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun draw(canvas: Canvas) {
        // Draw border first so any child views outside of the thumbnail bounds are drawn above it.
        focusBorderAnimator?.drawBorder(canvas)
        hoverBorderAnimator?.drawBorder(canvas)
        super.draw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val thumbnailTopMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        if (container.deviceProfile.isTablet) {
            pivotX = (if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else right - left).toFloat()
            pivotY = thumbnailTopMargin.toFloat()
        } else {
            pivotX = (right - left) * 0.5f
            pivotY = thumbnailTopMargin + (height - thumbnailTopMargin) * 0.5f
        }
        systemGestureExclusionRects =
            SYSTEM_GESTURE_EXCLUSION_RECT.onEach {
                it.right = width
                it.bottom = height
            }
        if (enableHoverOfChildElementsInTaskview()) {
            getThumbnailBounds(thumbnailBounds)
        }
    }

    override fun onRecycle() {
        resetPersistentViewTransforms()
        attachAlpha = 1f
        splitAlpha = 1f
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        if (!enableRefactorTaskThumbnail()) {
            taskContainers.forEach { it.thumbnailViewDeprecated.setThumbnail(it.task, null) }
        }
        setOverlayEnabled(false)
        onTaskListVisibilityChanged(false)
        borderEnabled = false
        hoverBorderVisible = false
        taskViewId = UNBOUND_TASK_VIEW_ID
        taskContainers.forEach { it.destroy() }
    }

    // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
    override fun hasOverlappingRendering() = false

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        with(info) {
            // Only make actions available if the app icon menu is visible to the user.
            // When modalness is >0, the user is in select mode and the icon menu is hidden.
            if (modalness == 0f) {
                addAction(
                    AccessibilityAction(
                        R.id.action_close,
                        context.getText(R.string.accessibility_close),
                    )
                )

                taskContainers.forEach {
                    TraceHelper.allowIpcs("TV.a11yInfo") {
                        TaskOverlayFactory.getEnabledShortcuts(this@TaskView, it).forEach { shortcut
                            ->
                            addAction(shortcut.createAccessibilityAction(context))
                        }
                    }
                }

                // Add DWB accessibility action at the end of the list
                taskContainers.forEach {
                    it.digitalWellBeingToast?.getDWBAccessibilityAction()?.let(::addAction)
                }
            }

            recentsView?.let {
                collectionItemInfo =
                    AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0,
                        1,
                        it.taskViewCount - it.indexOfChild(this@TaskView) - 1,
                        1,
                        false,
                    )
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        // TODO(b/343708271): Add support for multiple tasks per action.
        if (action == R.id.action_close) {
            recentsView?.dismissTask(this, true /*animateTaskView*/, true /*removeTask*/)
            return true
        }

        taskContainers.forEach {
            if (it.digitalWellBeingToast?.handleAccessibilityAction(action) == true) {
                return true
            }

            TaskOverlayFactory.getEnabledShortcuts(this, it).forEach { shortcut ->
                if (shortcut.hasHandlerForAction(action)) {
                    shortcut.onClick(this)
                    return true
                }
            }
        }

        return super.performAccessibilityAction(action, arguments)
    }

    /** Updates this task view to the given {@param task}. */
    open fun bind(
        task: Task,
        orientedState: RecentsOrientedState,
        taskOverlayFactory: TaskOverlayFactory,
    ) {
        cancelPendingLoadTasks()
        taskContainers =
            listOf(
                createTaskContainer(
                    task,
                    R.id.snapshot,
                    R.id.icon,
                    R.id.show_windows,
                    R.id.digital_wellbeing_toast,
                    STAGE_POSITION_UNDEFINED,
                    taskOverlayFactory,
                )
            )
        onBind(orientedState)
    }

    open fun onBind(orientedState: RecentsOrientedState) {
        taskContainers.forEach {
            it.bind()
            if (enableRefactorTaskThumbnail()) {
                it.thumbnailView.cornerRadius = thumbnailFullscreenParams.currentCornerRadius
            }
        }
        setOrientationState(orientedState)
    }

    protected fun createTaskContainer(
        task: Task,
        @IdRes thumbnailViewId: Int,
        @IdRes iconViewId: Int,
        @IdRes showWindowViewId: Int,
        @IdRes digitalWellbeingBannerId: Int,
        @StagePosition stagePosition: Int,
        taskOverlayFactory: TaskOverlayFactory,
    ): TaskContainer {
        val existingThumbnailView: View = findViewById(thumbnailViewId)!!
        val snapshotView =
            when {
                !enableRefactorTaskThumbnail() -> existingThumbnailView
                existingThumbnailView is TaskThumbnailView -> existingThumbnailView
                else -> {
                    val indexOfSnapshotView = indexOfChild(existingThumbnailView)
                    LayoutInflater.from(context)
                        .inflate(R.layout.task_thumbnail, this, false)
                        .also {
                            it.id = thumbnailViewId
                            addView(it, indexOfSnapshotView, existingThumbnailView.layoutParams)
                            removeView(existingThumbnailView)
                        }
                }
            }
        val iconView = getOrInflateIconView(iconViewId)
        return TaskContainer(
            this,
            task,
            snapshotView,
            iconView,
            TransformingTouchDelegate(iconView.asView()),
            stagePosition,
            findViewById(digitalWellbeingBannerId)!!,
            findViewById(showWindowViewId)!!,
            taskOverlayFactory,
        )
    }

    protected fun getOrInflateIconView(@IdRes iconViewId: Int): TaskViewIcon {
        val iconView = findViewById<View>(iconViewId)!!
        return iconView as? TaskViewIcon
            ?: (iconView as ViewStub)
                .apply {
                    layoutResource =
                        if (enableOverviewIconMenu()) R.layout.icon_app_chip_view
                        else R.layout.icon_view
                }
                .inflate() as TaskViewIcon
    }

    fun containsMultipleTasks() = taskContainers.size > 1

    /**
     * Returns the TaskContainer corresponding to a given taskId, or null if the TaskView does not
     * contain a Task with that ID.
     */
    fun getTaskContainerById(taskId: Int) = taskContainers.firstOrNull { it.task.key.id == taskId }

    /** Check if given `taskId` is tracked in this view */
    fun containsTaskId(taskId: Int) = getTaskContainerById(taskId) != null

    open fun setOrientationState(orientationState: RecentsOrientedState) {
        this.orientedState = orientationState
        taskContainers.forEach { it.iconView.setIconOrientation(orientationState, isGridTask) }
        setThumbnailOrientation(orientationState)
    }

    protected open fun setThumbnailOrientation(orientationState: RecentsOrientedState) {
        taskContainers.forEach {
            it.overlay.updateOrientationState(orientationState)
            it.digitalWellBeingToast?.initialize()
        }
    }

    /**
     * Updates TaskView scaling and translation required to support variable width if enabled, while
     * ensuring TaskView fits into screen in fullscreen.
     */
    open fun updateTaskSize(
        lastComputedTaskSize: Rect,
        lastComputedGridTaskSize: Rect,
        lastComputedCarouselTaskSize: Rect,
    ) {
        val thumbnailPadding = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        val taskWidth = lastComputedTaskSize.width()
        val taskHeight = lastComputedTaskSize.height()
        val nonGridScale: Float
        val boxTranslationY: Float
        val expectedWidth: Int
        val expectedHeight: Int
        if (container.deviceProfile.isTablet) {
            val boxWidth: Int
            val boxHeight: Int

            // Focused task and Desktop tasks should use focusTaskRatio that is associated
            // with the original orientation of the focused task.
            if (isLargeTile) {
                boxWidth = taskWidth
                boxHeight = taskHeight
            } else {
                // Otherwise task is in grid, and should use lastComputedGridTaskSize.
                boxWidth = lastComputedGridTaskSize.width()
                boxHeight = lastComputedGridTaskSize.height()
            }

            // Bound width/height to the box size.
            expectedWidth = boxWidth
            expectedHeight = boxHeight + thumbnailPadding

            // Scale to to fit task Rect.
            nonGridScale =
                if (enableGridOnlyOverview()) {
                    lastComputedCarouselTaskSize.width() / taskWidth.toFloat()
                } else {
                    taskWidth / boxWidth.toFloat()
                }

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - thumbnailPadding - taskHeight) / 2.0f
        } else {
            nonGridScale = 1f
            boxTranslationY = 0f
            expectedWidth = taskWidth
            expectedHeight = taskHeight + thumbnailPadding
        }
        this.nonGridScale = nonGridScale
        this.boxTranslationY = boxTranslationY
        updateLayoutParams<ViewGroup.LayoutParams> {
            width = expectedWidth
            height = expectedHeight
        }
        updateThumbnailSize()
    }

    protected open fun updateThumbnailSize() {
        // TODO(b/271468547), we should default to setting translations only on the snapshot instead
        //  of a hybrid of both margins and translations
        taskContainers[0].snapshotView.updateLayoutParams<LayoutParams> {
            topMargin = container.deviceProfile.overviewTaskThumbnailTopMarginPx
        }
        taskContainers.forEach { it.digitalWellBeingToast?.setupLayout() }
    }

    /** Returns the thumbnail's bounds, optionally relative to the screen. */
    @JvmOverloads
    open fun getThumbnailBounds(bounds: Rect, relativeToDragLayer: Boolean = false) {
        bounds.setEmpty()
        taskContainers.forEach {
            val thumbnailBounds = Rect()
            if (relativeToDragLayer) {
                container.dragLayer.getDescendantRectRelativeToSelf(
                    it.snapshotView,
                    thumbnailBounds,
                )
            } else {
                thumbnailBounds.set(it.snapshotView)
            }
            bounds.union(thumbnailBounds)
        }
    }

    /**
     * See [TaskDataChanges]
     *
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    fun onTaskListVisibilityChanged(visible: Boolean) {
        onTaskListVisibilityChanged(visible, FLAG_UPDATE_ALL)
    }

    /**
     * See [TaskDataChanges]
     *
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    open fun onTaskListVisibilityChanged(visible: Boolean, @TaskDataChanges changes: Int) {
        cancelPendingLoadTasks()
        val recentsModel = RecentsModel.INSTANCE.get(context)
        // These calls are no-ops if the data is already loaded, try and load the high
        // resolution thumbnail if the state permits
        if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL) && !enableRefactorTaskThumbnail()) {
            taskContainers.forEach {
                if (visible) {
                    recentsModel.thumbnailCache
                        .getThumbnailInBackground(it.task) { thumbnailData ->
                            it.task.thumbnail = thumbnailData
                            it.thumbnailViewDeprecated.setThumbnail(it.task, thumbnailData)
                        }
                        ?.also { request -> pendingThumbnailLoadRequests.add(request) }
                } else {
                    it.thumbnailViewDeprecated.setThumbnail(null, null)
                    // Reset the task thumbnail reference as well (it will be fetched from the
                    // cache or reloaded next time we need it)
                    it.task.thumbnail = null
                }
            }
        }
        if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
            taskContainers.forEach {
                if (visible) {
                    recentsModel.iconCache
                        .getIconInBackground(it.task) { icon, contentDescription, title ->
                            it.task.icon = icon
                            it.task.titleDescription = contentDescription
                            it.task.title = title
                            onIconLoaded(it)
                        }
                        ?.also { request -> pendingIconLoadRequests.add(request) }
                } else {
                    onIconUnloaded(it)
                }
            }
        }
        if (needsUpdate(changes, FLAG_UPDATE_CORNER_RADIUS)) {
            thumbnailFullscreenParams.updateCornerRadius(context)
        }
    }

    protected open fun needsUpdate(@TaskDataChanges dataChange: Int, @TaskDataChanges flag: Int) =
        (dataChange and flag) == flag

    protected open fun cancelPendingLoadTasks() {
        pendingThumbnailLoadRequests.forEach { it.cancel() }
        pendingThumbnailLoadRequests.clear()
        pendingIconLoadRequests.forEach { it.cancel() }
        pendingIconLoadRequests.clear()
    }

    protected open fun onIconLoaded(taskContainer: TaskContainer) {
        setIcon(taskContainer.iconView, taskContainer.task.icon)
        if (enableOverviewIconMenu()) {
            setText(taskContainer.iconView, taskContainer.task.title)
        }
        taskContainer.digitalWellBeingToast?.initialize()
    }

    protected open fun onIconUnloaded(taskContainer: TaskContainer) {
        setIcon(taskContainer.iconView, null)
        if (enableOverviewIconMenu()) {
            setText(taskContainer.iconView, null)
        }
    }

    protected fun setIcon(iconView: TaskViewIcon, icon: Drawable?) {
        with(iconView) {
            if (icon != null) {
                setDrawable(icon)
                setOnClickListener {
                    if (!confirmSecondSplitSelectApp()) {
                        showTaskMenu(this)
                    }
                }
                setOnLongClickListener {
                    requestDisallowInterceptTouchEvent(true)
                    showTaskMenu(this)
                }
            } else {
                setDrawable(null)
                setOnClickListener(null)
                setOnLongClickListener(null)
            }
        }
    }

    protected fun setText(iconView: TaskViewIcon, text: CharSequence?) {
        iconView.setText(text)
    }

    @JvmOverloads
    open fun setShouldShowScreenshot(
        shouldShowScreenshot: Boolean,
        thumbnailDatas: Map<Int, ThumbnailData?>? = null,
    ) {
        if (this.shouldShowScreenshot == shouldShowScreenshot) return
        this.shouldShowScreenshot = shouldShowScreenshot
        if (enableRefactorTaskThumbnail()) {
            return
        }

        taskContainers.forEach {
            val thumbnailData = thumbnailDatas?.get(it.task.key.id)
            if (thumbnailData != null) {
                it.thumbnailViewDeprecated.setThumbnail(it.task, thumbnailData)
            } else {
                it.thumbnailViewDeprecated.refresh()
            }
        }
    }

    private fun onClick() {
        if (confirmSecondSplitSelectApp()) {
            Log.d("b/310064698", "${taskIds.contentToString()} - onClick - split select is active")
            return
        }
        val callbackList =
            launchWithAnimation()?.apply {
                add {
                    Log.d("b/310064698", "${taskIds.contentToString()} - onClick - launchCompleted")
                }
            }
        Log.d("b/310064698", "${taskIds.contentToString()} - onClick - callbackList: $callbackList")
        container.statsLogManager
            .logger()
            .withItemInfo(firstItemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP)
    }

    /** Launch of the current task (both live and inactive tasks) with an animation. */
    fun launchWithAnimation(): RunnableList? {
        return if (isRunningTask && recentsView?.remoteTargetHandles != null) {
            launchAsLiveTile()
        } else {
            launchAsStaticTile()
        }
    }

    private fun launchAsLiveTile(): RunnableList? {
        val recentsView = recentsView ?: return null
        val remoteTargetHandles = recentsView.remoteTargetHandles
        if (!isClickableAsLiveTile) {
            Log.e(
                TAG,
                "launchAsLiveTile - TaskView is not clickable as a live tile; returning to home: ${taskIds.contentToString()}",
            )
            return null
        }
        isClickableAsLiveTile = false
        val targets =
            if (remoteTargetHandles.size == 1) {
                remoteTargetHandles[0].transformParams.targetSet
            } else {
                val apps =
                    remoteTargetHandles.flatMap { it.transformParams.targetSet.apps.asIterable() }
                val wallpapers =
                    remoteTargetHandles.flatMap {
                        it.transformParams.targetSet.wallpapers.asIterable()
                    }
                RemoteAnimationTargets(
                    apps.toTypedArray(),
                    wallpapers.toTypedArray(),
                    remoteTargetHandles[0].transformParams.targetSet.nonApps,
                    remoteTargetHandles[0].transformParams.targetSet.targetMode,
                )
            }
        if (targets == null) {
            // If the recents animation is cancelled somehow between the parent if block and
            // here, try to launch the task as a non live tile task.
            val runnableList = launchAsStaticTile()
            if (runnableList == null) {
                Log.e(
                    TAG,
                    "launchAsLiveTile - Recents animation cancelled and cannot launch task as non-live tile; returning to home: ${taskIds.contentToString()}",
                )
            }
            isClickableAsLiveTile = true
            return runnableList
        }
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "composeRecentsLaunchAnimator",
            taskIds.contentToString(),
        )
        val runnableList = RunnableList()
        with(AnimatorSet()) {
            TaskViewUtils.composeRecentsLaunchAnimator(
                this,
                this@TaskView,
                targets.apps,
                targets.wallpapers,
                targets.nonApps,
                true /* launcherClosing */,
                recentsView.stateManager,
                recentsView,
                recentsView.depthController,
            )
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (taskContainers.any { it.task.key.displayId != rootViewDisplayId }) {
                            launchAsStaticTile()
                        }
                        isClickableAsLiveTile = true
                        runEndCallback()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        runEndCallback()
                    }

                    private fun runEndCallback() {
                        runnableList.executeAllAndDestroy()
                    }
                }
            )
            start()
        }
        Log.d(TAG, "launchAsLiveTile - composeRecentsLaunchAnimator: ${taskIds.contentToString()}")
        recentsView.onTaskLaunchedInLiveTileMode()
        return runnableList
    }

    /**
     * Starts the task associated with this view and animates the startup.
     *
     * @return CompletionStage to indicate the animation completion or null if the launch failed.
     */
    open fun launchAsStaticTile(): RunnableList? {
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString(),
        )
        val opts =
            container.getActivityLaunchOptions(this, null).apply {
                options.launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
            }
        if (
            ActivityManagerWrapper.getInstance()
                .startActivityFromRecents(taskContainers[0].task.key, opts.options)
        ) {
            Log.d(
                TAG,
                "launchAsStaticTile - startActivityFromRecents: ${taskIds.contentToString()}",
            )
            ActiveGestureLog.INSTANCE.trackEvent(
                ActiveGestureErrorDetector.GestureEvent.EXPECTING_TASK_APPEARED
            )
            val recentsView = recentsView ?: return null
            if (
                recentsView.runningTaskViewId != -1 &&
                    recentsView.mRecentsAnimationController != null
            ) {
                recentsView.onTaskLaunchedInLiveTileMode()

                // Return a fresh callback in the live tile case, so that it's not accidentally
                // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                return RunnableList().also { recentsView.addSideTaskLaunchCallback(it) }
            }
            // If the recents transition is running (ie. in live tile mode), then the start
            // of a new task will merge into the existing transition and it currently will
            // not be run independently, so we need to rely on the onTaskAppeared() call
            // for the new task to trigger the side launch callback to flush this runnable
            // list (which is usually flushed when the app launch animation finishes)
            recentsView.addSideTaskLaunchCallback(opts.onEndCallback)
            return opts.onEndCallback
        } else {
            notifyTaskLaunchFailed("launchAsStaticTile")
            return null
        }
    }

    /** Starts the task associated with this view without any animation */
    @JvmOverloads
    open fun launchWithoutAnimation(
        isQuickSwitch: Boolean = false,
        callback: (launched: Boolean) -> Unit,
    ) {
        TestLogging.recordEvent(
            TestProtocol.SEQUENCE_MAIN,
            "startActivityFromRecentsAsync",
            taskIds.contentToString(),
        )
        val firstContainer = taskContainers[0]
        val failureListener = TaskRemovedDuringLaunchListener(context.applicationContext)
        if (isQuickSwitch) {
            // We only listen for failures to launch in quickswitch because the during this
            // gesture launcher is in the background state, vs other launches which are in
            // the actual overview state
            failureListener.register(container, firstContainer.task.key.id) {
                notifyTaskLaunchFailed("launchWithoutAnimation")
                recentsView?.let {
                    // Disable animations for now, as it is an edge case and the app usually
                    // covers launcher and also any state transition animation also gets
                    // clobbered by QuickstepTransitionManager.createWallpaperOpenAnimations
                    // when launcher shows again
                    it.startHome(false /* animated */)
                    // LauncherTaskbarUIController depends on the launcher state when
                    // checking whether to handle resume, but that can come in before
                    // startHome() changes the state, so force-refresh here to ensure the
                    // taskbar is updated
                    it.mSizeStrategy.taskbarController?.refreshResumedState()
                }
            }
        }
        // Indicate success once the system has indicated that the transition has started
        val opts =
            ActivityOptions.makeCustomTaskAnimation(
                    context,
                    0,
                    0,
                    Executors.MAIN_EXECUTOR.handler,
                    { callback(true) },
                ) {
                    failureListener.onTransitionFinished()
                }
                .apply {
                    launchDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
                    if (isQuickSwitch) {
                        setFreezeRecentTasksReordering()
                    }
                    disableStartingWindow = firstContainer.shouldShowSplashView
                }
        Executors.UI_HELPER_EXECUTOR.execute {
            if (
                !ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(firstContainer.task.key, opts)
            ) {
                // If the call to start activity failed, then post the result immediately,
                // otherwise, wait for the animation start callback from the activity options
                // above
                Executors.MAIN_EXECUTOR.post {
                    notifyTaskLaunchFailed("launchTask")
                    callback(false)
                }
            }
            Log.d(
                TAG,
                "launchWithoutAnimation - startActivityFromRecents: ${taskIds.contentToString()}",
            )
        }
    }

    private fun notifyTaskLaunchFailed(launchMethod: String) {
        val sb =
            StringBuilder("$launchMethod - Failed to launch task: ${taskIds.contentToString()}\n")
        taskContainers.forEach {
            sb.append("(task=${it.task.key.baseIntent} userId=${it.task.key.userId})\n")
        }
        Log.w(TAG, sb.toString())
        Toast.makeText(context, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
    }

    fun initiateSplitSelect(splitPositionOption: SplitPositionOption) {
        recentsView?.initiateSplitSelect(
            this,
            splitPositionOption.stagePosition,
            SplitConfigurationOptions.getLogEventForPosition(splitPositionOption.stagePosition),
        )
    }

    /**
     * Returns `true` if user is already in split select mode and this tap was to choose the second
     * app. `false` otherwise
     */
    protected open fun confirmSecondSplitSelectApp(): Boolean {
        val index = getLastSelectedChildTaskIndex()
        if (index >= taskContainers.size) {
            return false
        }
        val container = taskContainers[index]
        val recentsView = recentsView ?: return false
        return recentsView.confirmSplitSelect(
            this,
            container.task,
            container.iconView.drawable,
            container.snapshotView,
            container.splitAnimationThumbnail,
            /* intent */ null,
            /* user */ null,
            container.itemInfo,
        )
    }

    /**
     * Returns the task index of the last selected child task (0 or 1). If we contain multiple tasks
     * and this TaskView is used as part of split selection, the selected child task index will be
     * that of the remaining task.
     */
    protected open fun getLastSelectedChildTaskIndex() = 0

    private fun showTaskMenu(iconView: TaskViewIcon): Boolean {
        val recentsView = recentsView ?: return false
        if (!recentsView.canLaunchFullscreenTask()) {
            // Don't show menu when selecting second split screen app
            return true
        }
        if (!container.deviceProfile.isTablet && !recentsView.isClearAllHidden) {
            recentsView.snapToPage(recentsView.indexOfChild(this))
            return false
        }
        val menuContainer = taskContainers.firstOrNull { it.iconView === iconView } ?: return false
        container.statsLogManager
            .logger()
            .withItemInfo(menuContainer.itemInfo)
            .log(LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS)
        return showTaskMenuWithContainer(menuContainer)
    }

    private fun showTaskMenuWithContainer(menuContainer: TaskContainer): Boolean {
        val recentsView = recentsView ?: return false
        if (enableHoverOfChildElementsInTaskview()) {
            // Disable hover on all TaskView's whilst menu is showing.
            recentsView.setTaskBorderEnabled(false)
        }
        return if (enableOverviewIconMenu() && menuContainer.iconView is IconAppChipView) {
            menuContainer.iconView.revealAnim(/* isRevealing= */ true)
            TaskMenuView.showForTask(menuContainer) {
                menuContainer.iconView.revealAnim(/* isRevealing= */ false)
                if (enableHoverOfChildElementsInTaskview()) {
                    recentsView.setTaskBorderEnabled(true)
                }
            }
        } else if (container.deviceProfile.isTablet) {
            val alignedOptionIndex =
                if (
                    recentsView.isOnGridBottomRow(menuContainer.taskView) &&
                        container.deviceProfile.isLandscape
                ) {
                    if (enableGridOnlyOverview()) {
                        // With no focused task, there is less available space below the tasks, so
                        // align the arrow to the third option in the menu.
                        2
                    } else {
                        // Bottom row of landscape grid aligns arrow to second option to avoid
                        // clipping
                        1
                    }
                } else {
                    0
                }
            TaskMenuViewWithArrow.showForTask(menuContainer, alignedOptionIndex) {
                if (enableHoverOfChildElementsInTaskview()) {
                    recentsView.setTaskBorderEnabled(true)
                }
            }
        } else {
            TaskMenuView.showForTask(menuContainer) {
                if (enableHoverOfChildElementsInTaskview()) {
                    recentsView.setTaskBorderEnabled(true)
                }
            }
        }
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children that
     * might require special handling.
     */
    open fun offerTouchToChildren(event: MotionEvent): Boolean {
        taskContainers.forEach {
            if (event.action == MotionEvent.ACTION_DOWN) {
                computeAndSetIconTouchDelegate(it.iconView, tempCoordinates, it.iconTouchDelegate)
                if (it.iconTouchDelegate.onTouchEvent(event)) {
                    return true
                }
            }
        }
        return false
    }

    private fun computeAndSetIconTouchDelegate(
        view: TaskViewIcon,
        tempCenterCoordinates: FloatArray,
        transformingTouchDelegate: TransformingTouchDelegate,
    ) {
        val viewHalfWidth = view.width / 2f
        val viewHalfHeight = view.height / 2f
        Utilities.getDescendantCoordRelativeToAncestor(
            view.asView(),
            container.dragLayer,
            tempCenterCoordinates.apply {
                this[0] = viewHalfWidth
                this[1] = viewHalfHeight
            },
            false,
        )
        transformingTouchDelegate.setBounds(
            (tempCenterCoordinates[0] - viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] - viewHalfHeight).toInt(),
            (tempCenterCoordinates[0] + viewHalfWidth).toInt(),
            (tempCenterCoordinates[1] + viewHalfHeight).toInt(),
        )
    }

    /** Sets up an on-click listener and the visibility for show_windows icon on top of the task. */
    open fun setUpShowAllInstancesListener() {
        taskContainers.forEach {
            it.showWindowsView?.let { showWindowsView ->
                updateFilterCallback(
                    showWindowsView,
                    getFilterUpdateCallback(it.task.key.packageName),
                )
            }
        }
    }

    /**
     * Returns a callback that updates the state of the filter and the recents overview
     *
     * @param taskPackageName package name of the task to filter by
     */
    private fun getFilterUpdateCallback(taskPackageName: String?) =
        if (recentsView?.filterState?.shouldShowFilterUI(taskPackageName) == true)
            OnClickListener { recentsView?.setAndApplyFilter(taskPackageName) }
        else null

    /**
     * Sets the correct visibility and callback on the provided filterView based on whether the
     * callback is null or not
     */
    private fun updateFilterCallback(filterView: View, callback: OnClickListener?) {
        // Filtering changes alpha instead of the visibility since visibility
        // can be altered separately through RecentsView#resetFromSplitSelectionState()
        with(filterView) {
            alpha = if (callback == null) 0f else 1f
            setOnClickListener(callback)
        }
    }

    /**
     * Called to animate a smooth transition when going directly from an app into Overview (and vice
     * versa). Icons fade in, and DWB banners slide in with a "shift up" animation.
     */
    private fun onSettledProgressUpdated(settledProgress: Float) {
        taskContainers.forEach {
            it.iconView.setContentAlpha(settledProgress)
            it.digitalWellBeingToast?.bannerOffsetPercentage = 1f - settledProgress
        }
    }

    fun startIconFadeInOnGestureComplete() {
        iconFadeInOnGestureCompleteAnimator?.cancel()
        iconFadeInOnGestureCompleteAnimator =
            ObjectAnimator.ofFloat(settledProgressGesture, MULTI_PROPERTY_VALUE, 1f).apply {
                duration = FADE_IN_ICON_DURATION
                interpolator = Interpolators.LINEAR
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            iconFadeInOnGestureCompleteAnimator = null
                        }
                    }
                )
                start()
            }
    }

    fun setIconVisibleForGesture(isVisible: Boolean) {
        iconFadeInOnGestureCompleteAnimator?.cancel()
        settledProgressGesture.value = if (isVisible) 1f else 0f
    }

    /** Set a color tint on the snapshot and supporting views. */
    open fun setColorTint(amount: Float, tintColor: Int) {
        taskContainers.forEach {
            if (!enableRefactorTaskThumbnail()) {
                it.thumbnailViewDeprecated.dimAlpha = amount
            }
            it.iconView.setIconColorTint(tintColor, amount)
            it.digitalWellBeingToast?.setColorTint(tintColor, amount)
        }
    }

    /**
     * Sets visibility for the thumbnail and associated elements (DWB banners and action chips).
     * IconView is unaffected.
     *
     * @param taskId is only used when setting visibility to a non-[View.VISIBLE] value
     */
    open fun setThumbnailVisibility(visibility: Int, taskId: Int) {
        taskContainers.forEach {
            if (visibility == VISIBLE || it.task.key.id == taskId) {
                it.snapshotView.visibility = visibility
                it.digitalWellBeingToast?.visibility = visibility
                it.showWindowsView?.visibility = visibility
                it.overlay.setVisibility(visibility)
            }
        }
    }

    open fun setOverlayEnabled(overlayEnabled: Boolean) {
        if (!enableRefactorTaskThumbnail()) {
            taskContainers.forEach { it.setOverlayEnabled(overlayEnabled) }
        }
    }

    protected open fun refreshTaskThumbnailSplash() {
        if (!enableRefactorTaskThumbnail()) {
            taskContainers.forEach { it.thumbnailViewDeprecated.refreshSplashView() }
        }
    }

    protected fun getScrollAdjustment(gridEnabled: Boolean) =
        if (gridEnabled) gridTranslationX else nonGridTranslationX

    protected fun getOffsetAdjustment(gridEnabled: Boolean) = getScrollAdjustment(gridEnabled)

    fun getSizeAdjustment(fullscreenEnabled: Boolean) = if (fullscreenEnabled) nonGridScale else 1f

    private fun applyScale() {
        /* This is now only for grid mode on tablets. Unconditionally calling this breaks
         * our overview scrolling animation. Block this method, otherwise we have to
         * introduce a multivalue scale fusion class to handle this. */
        if (!container.deviceProfile.isTablet)
            return

        val scale = persistentScale * dismissScale
        scaleX = scale
        scaleY = scale
        updateFullscreenParams()
    }

    protected open fun applyThumbnailSplashAlpha() {
        if (!enableRefactorTaskThumbnail()) {
            taskContainers.forEach {
                it.thumbnailViewDeprecated.setSplashAlpha(taskThumbnailSplashAlpha)
            }
        }
    }

    private fun applyTranslationX() {
        translationX =
            dismissTranslationX +
                taskOffsetTranslationX +
                taskResistanceTranslationX +
                splitSelectTranslationX +
                gridEndTranslationX +
                persistentTranslationX
    }

    private fun applyTranslationY() {
        translationY =
            dismissTranslationY +
                taskOffsetTranslationY +
                taskResistanceTranslationY +
                splitSelectTranslationY +
                persistentTranslationY
    }

    private fun onGridProgressChanged() {
        applyTranslationX()
        applyTranslationY()
        applyScale()
    }

    protected open fun onFullscreenProgressChanged(fullscreenProgress: Float) {
        taskContainers.forEach {
            it.iconView.setVisibility(if (fullscreenProgress < 1) VISIBLE else INVISIBLE)
            it.overlay.setFullscreenProgress(fullscreenProgress)
        }
        settledProgressFullscreen.value =
            SETTLED_PROGRESS_FAST_OUT_INTERPOLATOR.getInterpolation(1 - fullscreenProgress)
        updateFullscreenParams()
    }

    protected open fun updateFullscreenParams() {
        updateFullscreenParams(thumbnailFullscreenParams)
        taskContainers.forEach {
            if (enableRefactorTaskThumbnail()) {
                it.thumbnailView.cornerRadius = thumbnailFullscreenParams.currentCornerRadius
            } else {
                it.thumbnailViewDeprecated.setFullscreenParams(thumbnailFullscreenParams)
            }
            it.overlay.setFullscreenParams(thumbnailFullscreenParams)
        }
    }

    protected fun updateFullscreenParams(fullscreenParams: FullscreenDrawParams) {
        recentsView?.let { fullscreenParams.setProgress(fullscreenProgress, it.scaleX, scaleX) }
    }

    private fun onModalnessUpdated(modalness: Float) {
        isClickable = modalness == 0f
        taskContainers.forEach {
            it.iconView.setModalAlpha(1 - modalness)
            it.digitalWellBeingToast?.bannerOffsetPercentage = modalness
        }
    }

    fun resetPersistentViewTransforms() {
        nonGridTranslationX = 0f
        gridTranslationX = 0f
        gridTranslationY = 0f
        boxTranslationY = 0f
        nonGridPivotTranslationX = 0f
        taskContainers.forEach {
            it.snapshotView.translationX = 0f
            it.snapshotView.translationY = 0f
        }
        resetViewTransforms()
    }

    fun resetViewTransforms() {
        // fullscreenTranslation and accumulatedTranslation should not be reset, as
        // resetViewTransforms is called during QuickSwitch scrolling.
        dismissTranslationX = 0f
        taskOffsetTranslationX = 0f
        taskResistanceTranslationX = 0f
        splitSelectTranslationX = 0f
        gridEndTranslationX = 0f
        dismissTranslationY = 0f
        taskOffsetTranslationY = 0f
        taskResistanceTranslationY = 0f
        if (recentsView?.isSplitSelectionActive != true) {
            splitSelectTranslationY = 0f
        }
        dismissScale = 1f
        translationZ = 0f
        setIconVisibleForGesture(true)
        settledProgressDismiss.value = 1f
        setColorTint(0f, 0)
    }

    private fun getGridTrans(endTranslation: Float) =
        Utilities.mapRange(gridProgress, 0f, endTranslation)

    private fun getNonGridTrans(endTranslation: Float) =
        endTranslation - getGridTrans(endTranslation)

    private fun MotionEvent.isWithinThumbnailBounds(): Boolean {
        return thumbnailBounds.contains(x.toInt(), y.toInt())
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        (if (isLayoutRtl) taskContainers.reversed() else taskContainers).forEach {
            it.addChildForAccessibility(outChildren)
        }
    }

    companion object {
        private const val TAG = "TaskView"
        const val FLAG_UPDATE_ICON = 1
        const val FLAG_UPDATE_THUMBNAIL = FLAG_UPDATE_ICON shl 1
        const val FLAG_UPDATE_CORNER_RADIUS = FLAG_UPDATE_THUMBNAIL shl 1
        const val FLAG_UPDATE_ALL =
            (FLAG_UPDATE_ICON or FLAG_UPDATE_THUMBNAIL or FLAG_UPDATE_CORNER_RADIUS)

        const val SETTLED_PROGRESS_INDEX_FULLSCREEN = 0
        const val SETTLED_PROGRESS_INDEX_GESTURE = 1
        const val SETTLED_PROGRESS_INDEX_DISMISS = 2
        const val SETTLED_PROGRESS_INDEX_COUNT = 3

        private const val ALPHA_INDEX_STABLE = 0
        private const val ALPHA_INDEX_ATTACH = 1
        private const val ALPHA_INDEX_SPLIT = 2

        private const val NUM_ALPHA_CHANNELS = 3

        /** The maximum amount that a task view can be scrimmed, dimmed or tinted. */
        const val MAX_PAGE_SCRIM_ALPHA = 0.4f
        const val FADE_IN_ICON_DURATION: Long = 120
        private const val DIM_ANIM_DURATION: Long = 700
        private const val SETTLE_TRANSITION_THRESHOLD =
            FADE_IN_ICON_DURATION.toFloat() / DIM_ANIM_DURATION
        val SETTLED_PROGRESS_FAST_OUT_INTERPOLATOR =
            Interpolators.clampToProgress(
                Interpolators.FAST_OUT_SLOW_IN,
                1f - SETTLE_TRANSITION_THRESHOLD,
                1f,
            )!!
        private val FADE_IN_ICON_INTERPOLATOR = Interpolators.LINEAR
        private val SYSTEM_GESTURE_EXCLUSION_RECT = listOf(Rect())

        private val SETTLED_PROGRESS: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("settleTransition") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.settledProgress = v
                }

                override fun get(taskView: TaskView) = taskView.settledProgress
            }

        private val SPLIT_SELECT_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("splitSelectTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.splitSelectTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.splitSelectTranslationX
            }

        private val SPLIT_SELECT_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("splitSelectTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.splitSelectTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.splitSelectTranslationY
            }

        private val DISMISS_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.dismissTranslationX
            }

        private val DISMISS_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.dismissTranslationY
            }

        private val TASK_OFFSET_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskOffsetTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskOffsetTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.taskOffsetTranslationX
            }

        private val TASK_OFFSET_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskOffsetTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskOffsetTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.taskOffsetTranslationY
            }

        private val TASK_RESISTANCE_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskResistanceTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskResistanceTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.taskResistanceTranslationX
            }

        private val TASK_RESISTANCE_TRANSLATION_Y: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("taskResistanceTranslationY") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.taskResistanceTranslationY = v
                }

                override fun get(taskView: TaskView) = taskView.taskResistanceTranslationY
            }

        @JvmField
        val GRID_END_TRANSLATION_X: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("gridEndTranslationX") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.gridEndTranslationX = v
                }

                override fun get(taskView: TaskView) = taskView.gridEndTranslationX
            }

        @JvmField
        val DISMISS_SCALE: FloatProperty<TaskView> =
            object : FloatProperty<TaskView>("dismissScale") {
                override fun setValue(taskView: TaskView, v: Float) {
                    taskView.dismissScale = v
                }

                override fun get(taskView: TaskView) = taskView.dismissScale
            }
    }
}
