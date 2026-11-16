/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.android.internal.R as InternalR
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Quickstep implementation of the helper class that creates and updates the blur drawable used
 * for folders and the homescreen popup.
 */
@ActivityContextSingleton
class QuickstepBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) : BlurBackgroundHelper() {

    private val context = activityContext.asContext()

    private val folderBlurRadius = context.resources.getDimension(R.dimen.folder_blur_radius)

    private val popupBlurRadius =
        context.resources.getDimensionPixelSize(R.dimen.popup_blur_radius)

    private val cornerRadius = Themes.getDialogCornerRadius(context)

    private val workspaceBlurScale =
        (folderBlurRadius / MIN_DOWNSCALED_BLUR_RADIUS).coerceIn(1f, MAX_WORKSPACE_BLUR_DOWNSCALE)

    private val workspaceBlurRenderNode =
        RenderNode("workspaceBlur").apply {
            val radius = folderBlurRadius / workspaceBlurScale
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            setPivotX(0f)
            setPivotY(0f)
            setScaleX(workspaceBlurScale)
            setScaleY(workspaceBlurScale)
            setClipToOutline(true)
        }
    private val workspaceBlurRenderNodeOutline = Outline()
    private val outlineMatrix = Matrix()
    private val lastOutlineRect = Rect()
    private val bounds = Rect()
    private val tmpRect = Rect()
    private var folderBlurDrawable: BackgroundBlurDrawable? = null
    private var blurOwnerFolder: Folder? = null

    private val popupBlurSurfaceColor: Int by lazy {
        val color =
            try {
                context.getColor(InternalR.color.customColorSurfaceEffect0)
            } catch (_: Exception) {
                context.getColor(R.color.materialColorSurfaceContainer)
            }
        if (Color.alpha(color) < 255) color
        else ColorUtils.setAlphaComponent(color, (0.60f * 255).toInt())
    }

    private fun getOrCreateFolderBlurDrawable(): BackgroundBlurDrawable? {
        folderBlurDrawable?.let {
            return it
        }
        val viewRoot = activityContext.dragLayer.viewRootImpl ?: return null
        return viewRoot.createBackgroundBlurDrawable()
            ?.apply {
                setBlurRadius(folderBlurRadius.toInt())
                setVisible(false, false)
            }
            .also { folderBlurDrawable = it }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isBlurEnabled()) {
            return
        }

        val dragLayer = activityContext.dragLayer
        val width = dragLayer.width
        val height = dragLayer.height
        if (width <= 0 || height <= 0) {
            return
        }
        blurOwnerFolder = folder

        val folderIcon = folder.folderIcon
        val folderNameVisibility: Int = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        // Exclude other folders attached to the drag layer to protect against
        // a possible RenderThread crash
        val openFolders =
            (0 until dragLayer.childCount)
                .mapNotNull { dragLayer.getChildAt(it) as? Folder }
                .filter { it.visibility == View.VISIBLE }
        openFolders.forEach { it.visibility = View.INVISIBLE }

        try {
            val scaledWidth = ceil(width / workspaceBlurScale).toInt()
            val scaledHeight = ceil(height / workspaceBlurScale).toInt()
            val canvas = workspaceBlurRenderNode.beginRecording(scaledWidth, scaledHeight)
            try {
                canvas.scale(1f / workspaceBlurScale, 1f / workspaceBlurScale)
                dragLayer.draw(canvas)
            } finally {
                workspaceBlurRenderNode.endRecording()
            }
            workspaceBlurRenderNode.setPosition(0, 0, scaledWidth, scaledHeight)
            lastOutlineRect.setEmpty()
        } finally {
            openFolders.forEach { it.visibility = View.VISIBLE }
            folderIcon.folderName.visibility = folderNameVisibility
            folderIcon.setIconVisible(isIconVisible)
        }
    }

    override fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isBlurEnabled()) {
            return
        }

        drawCrossWindowBlur(canvas, pathWrapper, view)
        drawWorkspaceBlur(canvas, pathWrapper?.path, view)
    }

    private fun drawWorkspaceBlur(canvas: Canvas, path: Path?, view: View) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.setTranslationX(-view.left.toFloat())
        workspaceBlurRenderNode.setTranslationY(-view.top.toFloat())

        val invScale = 1f / workspaceBlurScale
        if (path != null && !path.isEmpty) {
            workspaceBlurRenderNodeOutline.setPath(path)
            outlineMatrix.setTranslate(view.left.toFloat(), view.top.toFloat())
            outlineMatrix.postScale(invScale, invScale)
            workspaceBlurRenderNodeOutline.mPath.transform(outlineMatrix)
            workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
            lastOutlineRect.setEmpty()
        } else if (
            lastOutlineRect.left != view.left ||
                lastOutlineRect.top != view.top ||
                lastOutlineRect.right != view.right ||
                lastOutlineRect.bottom != view.bottom
        ) {
            lastOutlineRect.set(view.left, view.top, view.right, view.bottom)
            workspaceBlurRenderNodeOutline.setRoundRect(
                (view.left * invScale).roundToInt(),
                (view.top * invScale).roundToInt(),
                (view.right * invScale).roundToInt(),
                (view.bottom * invScale).roundToInt(),
                cornerRadius * invScale,
            )
            workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
        }
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = getOrCreateFolderBlurDrawable() ?: return

        val becameVisible = d.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            d.bounds = bounds
            d.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            bounds.set(0, 0, view.width, view.height)
            if (becameVisible || d.bounds != bounds) {
                d.bounds = bounds
            }
            d.setCornerRadius(cornerRadius)
        }
        d.draw(canvas)
    }

    override fun folderCloseComplete(folder: Folder) {
        if (blurOwnerFolder != null && blurOwnerFolder !== folder) {
            return
        }
        blurOwnerFolder = null
        if (workspaceBlurRenderNode.hasDisplayList()) {
            workspaceBlurRenderNode.discardDisplayList()
        }
        folderBlurDrawable?.setVisible(false, false)
    }

    override fun isBlurEnabled(): Boolean = blurState.value

    override fun isPopupBlurEnabled(): Boolean = isBlurEnabled() && isHomescreen()

    private fun isHomescreen(): Boolean {
        (activityContext as? Launcher)?.let { launcher ->
            if (launcher.isInState(LauncherState.ALL_APPS)) {
                return false
            }
        }
        activityContext.appsView?.let { appsView ->
            if (appsView.isInAllApps) {
                return false
            }
        }
        return true
    }

    override fun applyPopupBlurBackground(view: View): Boolean {
        if (!isPopupBlurEnabled()) {
            return false
        }
        val surfaceDrawable = view.background ?: return false
        if (getPopupBlurDrawable(view) != null) {
            return true
        }

        val viewRoot = activityContext.dragLayer.viewRootImpl ?: return false
        val surface = surfaceDrawable.mutate()
        val blurDrawable = viewRoot.createBackgroundBlurDrawable()?.apply {
            setBlurRadius(popupBlurRadius)
            getPopupCornerRadii(surface, view).applyTo(this)
            setVisible(true, false)
        } ?: return false
        view.background = LayerDrawable(arrayOf(blurDrawable, surface))
        view.invalidate()
        return true
    }

    override fun setPopupBlurAlpha(view: View, alpha: Float) {
        val d = getPopupBlurDrawable(view) ?: return
        val newAlpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        val oldAlpha = d.alpha
        if (oldAlpha == newAlpha) {
            return
        }
        d.alpha = newAlpha
        if (oldAlpha == 0) {
            refreshBlurPath(d)
            view.invalidate()
        }
    }

    private fun refreshBlurPath(d: BackgroundBlurDrawable) {
        tmpRect.set(d.bounds)
        d.bounds = tmpRect
    }

    override fun releasePopupBlurBackground(view: View) {
        getPopupBlurDrawable(view)?.setVisible(false, false)
    }

    private fun getPopupBlurDrawable(view: View): BackgroundBlurDrawable? {
        val background = view.background as? LayerDrawable ?: return null
        if (background.numberOfLayers < 2) {
            return null
        }
        return background.getDrawable(0) as? BackgroundBlurDrawable
    }

    private data class PopupCornerRadii(
        val topLeft: Float,
        val topRight: Float,
        val bottomLeft: Float,
        val bottomRight: Float,
    ) {
        fun applyTo(blurDrawable: BackgroundBlurDrawable) {
            blurDrawable.setCornerRadius(topLeft, topRight, bottomLeft, bottomRight)
        }
    }

    private fun getPopupCornerRadii(drawable: Drawable, view: View): PopupCornerRadii {
        if (drawable is GradientDrawable) {
            drawable.cornerRadii?.let { radii ->
                if (radii.size >= 8) {
                    return PopupCornerRadii(
                        maxOf(radii[0], radii[1]),
                        maxOf(radii[2], radii[3]),
                        maxOf(radii[6], radii[7]),
                        maxOf(radii[4], radii[5]),
                    )
                }
            }
            val radius = drawable.cornerRadius
            if (radius > 0f) {
                return PopupCornerRadii(radius, radius, radius, radius)
            }
        }
        val fallback = Themes.getDialogCornerRadius(view.context)
        return PopupCornerRadii(fallback, fallback, fallback, fallback)
    }

    override fun getPopupBlurSurfaceColor(fallbackColor: Int): Int {
        if (!isPopupBlurEnabled() || fallbackColor == Color.TRANSPARENT) {
            return fallbackColor
        }
        return popupBlurSurfaceColor
    }

    private companion object {
        const val MAX_WORKSPACE_BLUR_DOWNSCALE = 4f
        const val MIN_DOWNSCALED_BLUR_RADIUS = 8f
    }
}
