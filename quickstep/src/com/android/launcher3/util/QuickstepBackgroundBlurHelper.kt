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
import androidx.core.graphics.ColorUtils
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
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val bounds = Rect()
    private val folderBlurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isBlurEnabled()) null
        else
            activityContext.dragLayer.getViewRootImpl()
                .createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(folderBlurRadius.toInt())
                    setVisible(false, false)
                }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isBlurEnabled()) {
            return
        }

        val folderIcon = folder.folderIcon
        val folderNameVisibility: Int = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        val dragLayer = activityContext.dragLayer
        val canvas =
            workspaceBlurRenderNode.beginRecording(dragLayer.getWidth(), dragLayer.getHeight())
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.getWidth(), dragLayer.getHeight())

        folderIcon.folderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
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
        workspaceBlurRenderNode.translationX = -view.left.toFloat()
        workspaceBlurRenderNode.translationY = -view.top.toFloat()

        if (path != null && !path.isEmpty) {
            workspaceBlurRenderNodeOutline.setPath(path)
            workspaceBlurRenderNodeOutline.mPath.offset(view.left.toFloat(), view.top.toFloat())
        } else {
            workspaceBlurRenderNodeOutline.setRoundRect(
                view.left,
                view.top,
                view.right,
                view.bottom,
                cornerRadius,
            )
        }
        workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
        workspaceBlurRenderNode.setClipToOutline(true)

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                folderBlurRadius,
                folderBlurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = folderBlurDrawable ?: return

        d.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            d.bounds = bounds
            d.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            d.setBounds(0, 0, view.width, view.height)
            d.setCornerRadius(cornerRadius)
        }
        d.draw(canvas)
    }

    override fun folderCloseComplete() {
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

    override fun applyPopupBlurBackground(view: View) {
        if (!isPopupBlurEnabled()) {
            return
        }
        val surfaceDrawable = view.background ?: return
        if (surfaceDrawable is LayerDrawable
            && surfaceDrawable.numberOfLayers > 1
            && surfaceDrawable.getDrawable(0) is BackgroundBlurDrawable) {
            return
        }

        val viewRoot = activityContext.dragLayer.viewRootImpl ?: return
        val surface = surfaceDrawable.mutate()
        val blurDrawable = viewRoot.createBackgroundBlurDrawable()?.apply {
            setBlurRadius(popupBlurRadius)
            getPopupCornerRadii(surface, view).applyTo(this)
            setVisible(true, false)
        } ?: return
        view.background = LayerDrawable(arrayOf(blurDrawable, surface))
        view.invalidate()
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
        return getPopupBlurSurfaceColor()
    }

    private fun getPopupBlurSurfaceColor(): Int {
        val color =
            try {
                context.getColor(InternalR.color.surface_effect_0)
            } catch (_: Exception) {
                context.getColor(R.color.materialColorSurfaceContainer)
            }
        return if (Color.alpha(color) < 255) {
            color
        } else {
            ColorUtils.setAlphaComponent(color, (0.60f * 255).toInt())
        }
    }
}
