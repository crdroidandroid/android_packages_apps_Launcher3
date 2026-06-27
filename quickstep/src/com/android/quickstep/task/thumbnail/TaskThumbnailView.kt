/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isInvisible
import com.android.launcher3.Flags.enableRefactorTaskContentView
import com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA
import com.android.launcher3.R
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.ViewPool
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.views.FixedSizeImageView
import com.android.systemui.shared.recents.model.Task

class TaskThumbnailView : FrameLayout, ViewPool.Reusable {
    private val scrimView: View by lazy { findViewById(R.id.task_thumbnail_scrim) }
    private val liveTileView: LiveTileView by lazy { findViewById(R.id.task_thumbnail_live_tile) }
    private val thumbnailView: FixedSizeImageView by lazy { findViewById(R.id.task_thumbnail) }
    private val splashBackground: View by lazy { findViewById(R.id.splash_background) }
    private val splashIcon: FixedSizeImageView by lazy { findViewById(R.id.splash_icon) }
    private val dimAlpha: MultiPropertyFactory<View> by lazy {
        MultiPropertyFactory(scrimView, VIEW_ALPHA, ScrimViewAlpha.entries.size, ::maxOf)
    }
    private val outlinePath = Path()
    private var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    private var uiState: TaskThumbnailUiState = Uninitialized
    private var task: Task? = null
    private var lastMatrix: Matrix? = null
    private var overlaid = false

    /**
     * Sets the outline bounds of the view. Default to use view's bound as outline when set to null.
     */
    var outlineBounds: Rect? = null
        set(value) {
            field = value
            invalidateOutline()
        }

    private val bounds = Rect()

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (enableRefactorTaskContentView()) {
            return
        }
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val outlineRect = outlineBounds ?: bounds
                    outlinePath.apply {
                        rewind()
                        addRoundRect(
                            outlineRect.left.toFloat(),
                            outlineRect.top.toFloat(),
                            outlineRect.right.toFloat(),
                            outlineRect.bottom.toFloat(),
                            cornerRadius / scaleX,
                            cornerRadius / scaleY,
                            Path.Direction.CW,
                        )
                    }
                    outline.setPath(outlinePath)
                }
            }
    }

    override fun onRecycle() {
        uiState = Uninitialized
        task = null
        lastMatrix = null
        overlaid = false
        if (!enableRefactorTaskContentView()) {
            onSizeChanged = null
            outlineBounds = null
        }
        resetViews()
    }

    fun bind(task: Task) {
        this.task = task
    }

    fun setState(state: TaskThumbnailUiState, taskId: Int? = null) {
        val shouldOverlay = shouldDrawOverlay()
        if (uiState == state && overlaid == shouldOverlay) return
        logDebug("taskId: $taskId - uiState changed from: $uiState to: $state")
        uiState = state
        resetViews()
        if (shouldOverlay) {
            drawOverlayThumbnail()
            overlaid = true
            return
        }
        thumbnailView.scaleType = ImageView.ScaleType.MATRIX
        overlaid = false
        when (state) {
            is Uninitialized -> {}
            is LiveTile -> drawLiveWindow()
            is SnapshotSplash -> drawSnapshotSplash(state)
            is BackgroundOnly -> drawBackground(state.backgroundColor)
        }
        if (state is SnapshotSplash) {
            lastMatrix?.let { thumbnailView.imageMatrix = it }
        }
    }

    /**
     * Updates the alpha of the dim layer on top of this view. If dimAlpha is 0, no dimming is
     * applied; if dimAlpha is 1, the thumbnail will be the extracted background color.
     *
     * @param tintAmount The amount of alpha that will be applied to the dim layer.
     */
    fun updateTintAmount(tintAmount: Float) {
        dimAlpha[ScrimViewAlpha.TintAmount.ordinal].value = tintAmount
    }

    fun updateMenuOpenProgress(progress: Float) {
        dimAlpha[ScrimViewAlpha.MenuProgress.ordinal].value = progress * MAX_SCRIM_ALPHA
    }

    fun updateSplashAlpha(value: Float) {
        splashBackground.alpha = value
        splashIcon.alpha = value
    }

    fun doOnSizeChange(action: (width: Int, height: Int) -> Unit) {
        if (enableRefactorTaskContentView()) {
            return
        }
        onSizeChanged = action
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (enableRefactorTaskContentView()) {
            return
        }
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(width, height)
        bounds.set(0, 0, w, h)
        invalidateOutline()
    }

    override fun setScaleX(scaleX: Float) {
        if (enableRefactorTaskContentView()) {
            return
        }
        super.setScaleX(scaleX)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleX = 1 / scaleX
    }

    override fun setScaleY(scaleY: Float) {
        if (enableRefactorTaskContentView()) {
            return
        }
        super.setScaleY(scaleY)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleY = 1 / scaleY
    }

    fun parentScaleXUpdated(scaleX: Float) {
        // Splash icon should ignore scale on TTV
        splashIcon.scaleX = 1 / scaleX
    }

    fun parentScaleYUpdated(scaleY: Float) {
        // Splash icon should ignore scale on TTV
        splashIcon.scaleY = 1 / scaleY
    }

    private fun resetViews() {
        liveTileView.isInvisible = true
        thumbnailView.isInvisible = true
        thumbnailView.setImageBitmap(null)
        splashBackground.alpha = 0f
        splashBackground.setBackgroundColor(Color.TRANSPARENT)
        splashIcon.alpha = 0f
        splashIcon.setImageDrawable(null)
        scrimView.alpha = 0f
        alpha = 1.0f
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun shouldDrawOverlay(): Boolean {
        return task?.isLocked == true || isCameraSnapshot()
    }

    private fun isCameraSnapshot(): Boolean {
        val packageName = task?.topComponent?.packageName ?: return false
        return packageName.contains("camera", ignoreCase = true) ||
                packageName.contains("aperture", ignoreCase = true)
    }

    private fun drawOverlayThumbnail() {
        val icon = if (task?.isLocked == true) {
            R.drawable.ic_recent_app_locked
        } else {
            R.drawable.ic_recent_camera_locked
        }
        thumbnailView.setImageResource(icon)
        thumbnailView.scaleType = ImageView.ScaleType.CENTER
        thumbnailView.isInvisible = false
        drawBackground(context.getColor(R.color.recent_app_locked_bg_color))
    }

    private fun drawBackground(@ColorInt background: Int) {
        setBackgroundColor(background)
    }

    private fun drawLiveWindow() {
        liveTileView.isInvisible = false
    }

    private fun drawSnapshotSplash(snapshotSplash: SnapshotSplash) {
        drawSnapshot(snapshotSplash.snapshot)

        splashBackground.setBackgroundColor(snapshotSplash.snapshot.backgroundColor)
        val icon = snapshotSplash.splash?.constantState?.newDrawable()?.mutate() ?: ShapeDrawable()
        splashIcon.setImageDrawable(icon)
    }

    private fun drawSnapshot(snapshot: Snapshot) {
        // Always draw the background since the snapshots might be translucent or partially empty
        // E.g. reparented tasks from drag-to-dismiss split screen.
        drawBackground(snapshot.backgroundColor)
        thumbnailView.setImageBitmap(snapshot.bitmap)
        thumbnailView.isInvisible = false
    }

    fun setImageMatrix(matrix: Matrix) {
        lastMatrix = matrix
        if (uiState is SnapshotSplash && !overlaid) {
            thumbnailView.imageMatrix = matrix
        }
    }

    private fun logDebug(message: String) {
        Log.d(TAG, "[TaskThumbnailView@${Integer.toHexString(hashCode())}] $message")
    }

    private companion object {
        const val TAG = "TaskThumbnailView"
        private const val MAX_SCRIM_ALPHA = 0.4f

        enum class ScrimViewAlpha {
            MenuProgress,
            TintAmount,
        }
    }
}
