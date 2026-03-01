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

package com.android.launcher3.folder

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.Themes
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject
import javax.inject.Named

/**
 * Quickstep implementation of the helper class that creates and updates the blur drawable used
 * for folders.
 */
@ActivityContextSingleton
class QuickstepFolderBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) : FolderBlurBackgroundHelper() {

    private val blurRadius = activityContext.asContext().resources.getDimension(
        R.dimen.folder_blur_radius
    )
    private val cornerRadius = Themes.getDialogCornerRadius(activityContext.asContext())
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val bounds = Rect()
    private val blurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isFolderBlurStyleEnabled()) null
        else
            activityContext.dragLayer.getViewRootImpl()
                .createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(blurRadius.toInt())
                    setVisible(false, false)
                }
    }

    override fun prepareToOpen(folder: Folder) {
        if (!isFolderBlurStyleEnabled()) {
            return
        }

        val folderIcon = folder.mFolderIcon
        val folderNameVisibility: Int = folderIcon.mFolderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        val dragLayer = activityContext.dragLayer
        val canvas = workspaceBlurRenderNode.beginRecording(dragLayer.getWidth(), dragLayer.getHeight())
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.getWidth(), dragLayer.getHeight())

        folderIcon.mFolderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
    }

    override fun drawBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isFolderBlurStyleEnabled()) {
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

        if (path != null) {
            workspaceBlurRenderNodeOutline.setPath(path)
            workspaceBlurRenderNodeOutline.mPath.offset(view.left.toFloat(), view.top.toFloat())

            workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
            workspaceBlurRenderNode.setClipToOutline(true)
        }

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = blurDrawable ?: return

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
        blurDrawable?.setVisible(false, false)
    }

    private fun isFolderBlurStyleEnabled(): Boolean {
        return Flags.blurOnMoreSurfaces() && blurState.value
    }
}
